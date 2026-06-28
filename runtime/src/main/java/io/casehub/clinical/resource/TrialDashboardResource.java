package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.entity.*;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/trials/{trialId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class TrialDashboardResource {

    @Inject CurrentPrincipal principal;

    // --- Response records (nested per clinical convention) ---

    public record TrialSummary(
        String protocolId, String phase, String sponsor, int targetEnrollment,
        long totalEnrolled, long totalAdverseEvents, long totalDeviations
    ) {}

    public record PatientRow(
        UUID id, UUID siteId, String patientId, String enrollmentStatus,
        String screeningResult, String consentStatus
    ) {}

    public record AdverseEventRow(
        UUID id, UUID enrollmentId, UUID siteId, String grade, String type,
        Instant reportedAt, Instant slaDeadline, String escalationStatus,
        String regulatorySubmissionStatus, String slaTimeRemaining
    ) {}

    public record DeviationRow(
        UUID id, UUID siteId, String deviationType, String severity,
        String piApprovalStatus, Instant commandedAt
    ) {}

    // --- Endpoints ---

    @GET
    @Path("/summary")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response summary(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();

        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();

        long enrolled = siteIds.isEmpty() ? 0 :
            PatientEnrollment.count("siteId in ?1 and tenantId = ?2",
                siteIds, principal.tenancyId());

        long aeCount = 0;
        if (!siteIds.isEmpty()) {
            List<PatientEnrollment> enrollments = PatientEnrollment
                .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
                .list();
            List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();
            if (!enrollmentIds.isEmpty()) {
                aeCount = AdverseEvent.count("enrollmentId in ?1 and tenantId = ?2",
                    enrollmentIds, principal.tenancyId());
            }
        }

        long devCount = siteIds.isEmpty() ? 0 :
            ProtocolDeviation.count("siteId in ?1 and tenantId = ?2",
                siteIds, principal.tenancyId());

        return Response.ok(new TrialSummary(
            trial.protocolId, trial.phase.name(), trial.sponsor,
            trial.targetEnrollment, enrolled, aeCount, devCount
        )).build();
    }

    @GET
    @Path("/patients")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response patients(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();

        if (siteIds.isEmpty()) return Response.ok(List.of()).build();

        List<PatientEnrollment> enrollments = PatientEnrollment
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();

        List<PatientRow> rows = enrollments.stream().map(e -> new PatientRow(
            e.id, e.siteId, e.patientId,
            e.enrollmentStatus != null ? e.enrollmentStatus.name() : null,
            e.screeningResult != null ? e.screeningResult.name() : null,
            e.consentStatus != null ? e.consentStatus.name() : null
        )).toList();

        return Response.ok(rows).build();
    }

    @GET
    @Path("/adverse-events")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response adverseEvents(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        // Two-hop: trial → sites → enrollments → AEs
        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) return Response.ok(List.of()).build();

        List<PatientEnrollment> enrollments = PatientEnrollment
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();
        if (enrollments.isEmpty()) return Response.ok(List.of()).build();

        List<UUID> enrollmentIds = enrollments.stream().map(e -> e.id).toList();
        // Build siteId lookup from enrollment
        Map<UUID, UUID> enrollmentToSite = enrollments.stream()
            .collect(Collectors.toMap(e -> e.id, e -> e.siteId));

        List<AdverseEvent> aes = AdverseEvent
            .find("enrollmentId in ?1 and tenantId = ?2", enrollmentIds, principal.tenancyId())
            .list();

        Instant now = Instant.now();
        List<AdverseEventRow> rows = aes.stream().map(ae -> {
            String slaRemaining = null;
            if (ae.slaDeadline != null) {
                Duration remaining = Duration.between(now, ae.slaDeadline);
                if (remaining.isNegative()) {
                    slaRemaining = "OVERDUE by " + formatDuration(remaining.abs());
                } else {
                    slaRemaining = formatDuration(remaining) + " remaining";
                }
            }
            return new AdverseEventRow(
                ae.id, ae.enrollmentId, enrollmentToSite.get(ae.enrollmentId),
                ae.grade != null ? ae.grade.name() : null, null,
                ae.reportedAt, ae.slaDeadline,
                ae.escalationStatus != null ? ae.escalationStatus.name() : null,
                ae.regulatorySubmissionStatus != null ? ae.regulatorySubmissionStatus.name() : null,
                slaRemaining
            );
        }).toList();

        return Response.ok(rows).build();
    }

    @GET
    @Path("/deviations")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response deviations(@PathParam("trialId") UUID trialId) {
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null) return Response.status(404).build();

        List<TrialSite> sites = TrialSite.find("trialId = ?1 and tenantId = ?2",
            trialId, principal.tenancyId()).list();
        List<UUID> siteIds = sites.stream().map(s -> s.id).toList();
        if (siteIds.isEmpty()) return Response.ok(List.of()).build();

        List<ProtocolDeviation> devs = ProtocolDeviation
            .find("siteId in ?1 and tenantId = ?2", siteIds, principal.tenancyId())
            .list();

        List<DeviationRow> rows = devs.stream().map(d -> new DeviationRow(
            d.id, d.siteId, d.deviationType,
            d.severity != null ? d.severity.name() : null,
            d.piApprovalStatus != null ? d.piApprovalStatus.name() : null,
            d.commandedAt
        )).toList();

        return Response.ok(rows).build();
    }

    private static String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours > 24) return (hours / 24) + "d " + (hours % 24) + "h";
        return hours + "h " + minutes + "m";
    }
}
