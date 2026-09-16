package io.casehub.clinical.service.api;

import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.spi.ClinicalAdverseEventApi;
import io.casehub.clinical.api.view.AdverseEventView;
import io.casehub.clinical.api.view.GradeHistoryEntry;
import io.casehub.clinical.api.view.RegradeRequest;
import io.casehub.clinical.api.view.ReportAdverseEventRequest;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.AeGradeChange;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.clinical.service.AdverseEventService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalAdverseEventApi implements ClinicalAdverseEventApi {

    @Inject AdverseEventService adverseEventService;
    @Inject
            EntityManager       em;


    @Override
    public List<AdverseEventView> listAdverseEvents(UUID trialId, UUID siteId, UUID enrollmentId, String tenancyId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) return List.of();
        List<AdverseEvent> events = em.createQuery("SELECT a FROM AdverseEvent a WHERE a.enrollmentId = :enrollmentId AND a.tenantId = :tenantId", AdverseEvent.class).setParameter("enrollmentId", enrollmentId).setParameter("tenantId", enrollment.tenantId).getResultList();
        return events.stream().map(DefaultClinicalAdverseEventApi::toView).toList();
    }

    @Override
    public AdverseEventView reportAdverseEvent(UUID trialId, UUID siteId, UUID enrollmentId,
                                                ReportAdverseEventRequest req, String tenancyId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) throw new NotFoundException("Enrollment not found");
        TrialSite site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (site == null || !site.trialId.equals(trialId)) throw new NotFoundException("Site not found");

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = enrollmentId;
        ae.grade = req.grade();
        ae.actuality = req.actuality() != null ? req.actuality() : EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = req.occurredAt();
        ae.unexpected = req.unexpected() != null ? req.unexpected() : false;
        ae.suspected = req.suspected() != null ? req.suspected() : true;

        adverseEventService.reportAdverseEvent(ae);
        return toView(ae);
    }

    @Override
    public AdverseEventView getAdverseEvent(UUID trialId, UUID siteId, UUID enrollmentId, UUID aeId, String tenancyId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) return null;
        TrialSite site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (site == null || !site.trialId.equals(trialId)) return null;
        AdverseEvent ae = em.createNamedQuery("AdverseEvent.findByIdAndTenantId", AdverseEvent.class).setParameter("id", aeId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (ae == null || !ae.enrollmentId.equals(enrollmentId)) return null;
        return toView(ae);
    }

    @Override
    @Transactional
    public AdverseEventView regradeAdverseEvent(UUID trialId, UUID siteId, UUID enrollmentId,
                                                  UUID aeId, RegradeRequest req,
                                                  String tenancyId, String actorId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) throw new NotFoundException("Enrollment not found");
        TrialSite site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (site == null || !site.trialId.equals(trialId)) throw new NotFoundException("Site not found");
        AdverseEvent ae = em.createNamedQuery("AdverseEvent.findByIdAndTenantId", AdverseEvent.class).setParameter("id", aeId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (ae == null || !ae.enrollmentId.equals(enrollmentId)) throw new NotFoundException("Adverse event not found");

        adverseEventService.regradeAdverseEvent(aeId, req.grade(), actorId, req.reason());
        ae = em.find(AdverseEvent.class, aeId);
        return toView(ae);
    }

    @Override
    public List<GradeHistoryEntry> getGradeHistory(UUID trialId, UUID siteId, UUID enrollmentId,
                                                     UUID aeId, String tenancyId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId)) return List.of();
        TrialSite site = em.createNamedQuery("TrialSite.findByIdAndTenantId", TrialSite.class).setParameter("id", siteId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (site == null || !site.trialId.equals(trialId)) return List.of();
        AdverseEvent ae = em.createNamedQuery("AdverseEvent.findByIdAndTenantId", AdverseEvent.class).setParameter("id", aeId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (ae == null || !ae.enrollmentId.equals(enrollmentId)) return List.of();

        return em.createNamedQuery("AeGradeChange.findByAdverseEventId", AeGradeChange.class).setParameter("aeId", aeId).getResultList().stream()
                .map(gc -> new GradeHistoryEntry(gc.id,
                        gc.previousGrade != null ? gc.previousGrade.name() : null,
                        gc.newGrade.name(), gc.changedAt, gc.changedBy, gc.reason))
                .toList();
    }

    static AdverseEventView toView(AdverseEvent ae) {
        return new AdverseEventView(ae.id, ae.enrollmentId, ae.grade, ae.actuality, ae.outcome,
                ae.eventType, ae.occurredAt, ae.reportedAt, ae.slaDeadline, ae.escalationStatus,
                ae.unexpected, ae.suspected, ae.susarOversightStatus, ae.regulatorySubmissionStatus);
    }
}
