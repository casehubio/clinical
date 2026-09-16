package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TenantEntityLookup;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.service.ConsentWithdrawalService;
import io.casehub.clinical.service.PatientEnrollmentNotFoundException;
import io.casehub.clinical.service.WithdrawalResult;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerProvExportService;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PatientComplianceResource {

    @Inject ConsentWithdrawalService consentWithdrawalService;
    @Inject LedgerProvExportService ledgerProvExportService;
    @Inject LedgerVerificationService ledgerVerificationService;
    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject CurrentPrincipal principal;
    @Inject
            jakarta.persistence.EntityManager em;


    public record LedgerVerifyResponse(boolean valid, String merkleRoot) {}

    @GET
    @Path("/ledger/verify")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response verifyLedger(@PathParam("trialId") UUID trialId,
                                  @PathParam("siteId") UUID siteId,
                                  @PathParam("enrollmentId") UUID enrollmentId) {
        PatientEnrollment enrollment = TenantEntityLookup.findByIdForTenant(em, PatientEnrollment.class, enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TenantEntityLookup.findByIdForTenant(em, TrialSite.class, siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        String ledgerTenantId = "default";
        boolean valid = ledgerVerificationService.verify(enrollmentId, ledgerTenantId);
        String merkleRoot = null;
        try {
            merkleRoot = ledgerVerificationService.treeRoot(enrollmentId, ledgerTenantId);
        } catch (IllegalStateException ignored) {}
        return Response.ok(new LedgerVerifyResponse(valid, merkleRoot)).build();
    }

    @POST
    @Path("/withdraw-consent")
    @RolesAllowed(ClinicalGroups.INVESTIGATOR)
    public Response withdrawConsent(@PathParam("trialId") UUID trialId,
                                     @PathParam("siteId") UUID siteId,
                                     @PathParam("enrollmentId") UUID enrollmentId) {
        PatientEnrollment enrollment = TenantEntityLookup.findByIdForTenant(em, PatientEnrollment.class, enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TenantEntityLookup.findByIdForTenant(em, TrialSite.class, siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        try {
            WithdrawalResult result = consentWithdrawalService.withdraw(enrollmentId, principal.tenancyId());
            if (result == WithdrawalResult.ALREADY_WITHDRAWN) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("Consent already withdrawn for enrollment " + enrollmentId).build();
            }
            return Response.noContent().build();
        } catch (PatientEnrollmentNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/audit/prov")
    @Produces("application/ld+json")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response getAuditProv(@PathParam("trialId") UUID trialId,
                                  @PathParam("siteId") UUID siteId,
                                  @PathParam("enrollmentId") UUID enrollmentId) {
        PatientEnrollment enrollment = TenantEntityLookup.findByIdForTenant(em, PatientEnrollment.class, enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TenantEntityLookup.findByIdForTenant(em, TrialSite.class, siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        try {
            String jsonLd = ledgerProvExportService.exportSubject(enrollmentId, principal.tenancyId());
            return Response.ok(jsonLd).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("/audit/entries/{entryId}/proof")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response getMerkleProof(@PathParam("trialId") UUID trialId,
                                    @PathParam("siteId") UUID siteId,
                                    @PathParam("enrollmentId") UUID enrollmentId,
                                    @PathParam("entryId") UUID entryId) {
        PatientEnrollment enrollment = TenantEntityLookup.findByIdForTenant(em, PatientEnrollment.class, enrollmentId, principal);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            return Response.status(Response.Status.NOT_FOUND).build();
        TrialSite site = TenantEntityLookup.findByIdForTenant(em, TrialSite.class, siteId, principal);
        if (site == null || !site.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        var ledgerEntry = ledgerEntryRepository.findEntryById(entryId, principal.tenancyId()).orElse(null);
        if (ledgerEntry == null || !enrollmentId.equals(ledgerEntry.subjectId))
            return Response.status(Response.Status.NOT_FOUND).build();
        try {
            var proof = ledgerVerificationService.inclusionProof(entryId, principal.tenancyId());
            return Response.ok(proof).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
