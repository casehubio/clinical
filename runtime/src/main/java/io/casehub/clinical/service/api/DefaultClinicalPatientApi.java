package io.casehub.clinical.service.api;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.api.model.EvaluateScreenRequest;
import io.casehub.clinical.api.spi.ClinicalPatientApi;
import io.casehub.clinical.api.view.EnrollPatientRequest;
import io.casehub.clinical.api.view.PatientEnrollmentView;
import io.casehub.clinical.api.view.ScreenPatientRequest;
import io.casehub.clinical.api.view.ScreenResponse;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.entity.TenantEntityLookup;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.clinical.service.EligibilityScreeningService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalPatientApi implements ClinicalPatientApi {

    @Inject EligibilityScreeningService eligibilityScreeningService;
    @Inject CurrentPrincipal principal;
    @Inject
            EntityManager               em;
    @Inject io.casehub.platform.api.identity.CurrentPrincipal currentPrincipal;
    @Inject io.casehub.clinical.cbr.SiteEnrollmentAlertService siteEnrollmentAlertService;

    @Override
    public List<PatientEnrollmentView> listPatients(UUID trialId, UUID siteId, String tenancyId) {
        TrialSite site = TenantEntityLookup.findByIdForTenant(em, TrialSite.class, siteId, principal);
        if (site == null || !site.trialId.equals(trialId)) return List.of();
        List<PatientEnrollment> enrollments = em.createQuery("SELECT e FROM PatientEnrollment e WHERE e.siteId = :siteId AND e.tenantId = :tenantId", PatientEnrollment.class).setParameter("siteId", siteId).setParameter("tenantId", site.tenantId).getResultList();
        return enrollments.stream().map(DefaultClinicalPatientApi::toView).toList();
    }

    @Override
    @Transactional
    public PatientEnrollmentView enrollPatient(UUID trialId, UUID siteId, EnrollPatientRequest req, String tenancyId) {
        TrialSite site;
        if (currentPrincipal.isCrossTenantAdmin()) {
            site = em.find(TrialSite.class, siteId);
        } else {
            site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        }
        if (site == null || !site.trialId.equals(trialId))
            throw new NotFoundException("Site not found");

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.tenantId = site.tenantId;
        enrollment.siteId = siteId;
        enrollment.patientId = req.patientId();
        enrollment.consentStatus = ConsentStatus.PENDING;
        enrollment.enrollmentStatus = EnrollmentStatus.CANDIDATE;
        em.persist(enrollment);
        try { siteEnrollmentAlertService.evaluate(siteId, site.trialId, enrollment.tenantId); } catch (Exception ignored) {}
        return toView(enrollment);
    }

    @Override
    public PatientEnrollmentView getPatient(UUID trialId, UUID siteId, UUID enrollmentId, String tenancyId) {
        PatientEnrollment enrollment;
        if (currentPrincipal.isCrossTenantAdmin()) {
            enrollment = em.find(PatientEnrollment.class, enrollmentId);
        } else {
            enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        }
        if (enrollment == null || !enrollment.siteId.equals(siteId)) return null;
        TrialSite site;
        if (currentPrincipal.isCrossTenantAdmin()) {
            site = em.find(TrialSite.class, siteId);
        } else {
            site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        }
        if (site == null || !site.trialId.equals(trialId)) return null;
        return toView(enrollment);
    }

    @Override
    @Transactional
    public ScreenResponse screenPatient(UUID trialId, UUID siteId, UUID enrollmentId,
                                         ScreenPatientRequest req, String tenancyId) {
        if (req == null || req.criteria() == null || req.criteria().isEmpty()) {
            throw new BadRequestException("At least one screening criterion is required");
        }
        TrialSite site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (site == null || !site.trialId.equals(trialId)) throw new NotFoundException("Site not found");
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) throw new NotFoundException("Enrollment not found");
        if (enrollment.screeningResult != null)
            throw new jakarta.ws.rs.ClientErrorException("Patient already screened", 409);

        if (req.criteria() == null || req.criteria().isEmpty()) throw new BadRequestException("criteria must not be empty");
        eligibilityScreeningService.screen(enrollment, req.criteria());
        return new ScreenResponse(enrollment.enrollmentStatus.name(),
                enrollment.screeningResult != null ? enrollment.screeningResult.name() : null);
    }

    @Override
    @Transactional
    public ScreenResponse evaluateAndScreen(UUID trialId, UUID siteId, UUID enrollmentId,
                                             EvaluateScreenRequest req, String tenancyId) {
        if (req == null || req.protocolCriteria() == null || req.protocolCriteria().isEmpty()) {
            throw new BadRequestException("At least one protocol criterion is required");
        }
        TrialSite site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (site == null || !site.trialId.equals(trialId)) throw new NotFoundException("Site not found");
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) throw new NotFoundException("Enrollment not found");
        if (enrollment.screeningResult != null)
            throw new jakarta.ws.rs.ClientErrorException("Patient already screened", 409);

        eligibilityScreeningService.evaluateAndScreen(enrollment, req.protocolCriteria());
        return new ScreenResponse(enrollment.enrollmentStatus.name(),
                enrollment.screeningResult != null ? enrollment.screeningResult.name() : null);
    }

    static PatientEnrollmentView toView(PatientEnrollment e) {
        return new PatientEnrollmentView(e.id, e.siteId, e.patientId, e.consentStatus,
                e.enrollmentStatus, e.enrolledAt, e.withdrawnAt, e.screeningResult,
                e.screeningCompletedAt, e.eligibilityScreeningCaseStatus, e.treatmentArm);
    }
}
