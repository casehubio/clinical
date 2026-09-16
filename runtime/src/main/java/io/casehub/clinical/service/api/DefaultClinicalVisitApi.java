package io.casehub.clinical.service.api;

import io.casehub.clinical.api.spi.ClinicalVisitApi;
import io.casehub.clinical.api.view.ScheduleVisitRequest;
import io.casehub.clinical.api.view.UpdateVisitRequest;
import io.casehub.clinical.api.view.VisitView;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.Visit;
import io.casehub.clinical.service.VisitLedgerWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalVisitApi implements ClinicalVisitApi {

    @Inject VisitLedgerWriter ledgerWriter;
    @Inject
            jakarta.persistence.EntityManager em;


    @Override
    @Transactional
    public VisitView createVisit(UUID trialId, UUID siteId, UUID enrollmentId,
                                  ScheduleVisitRequest req, String tenancyId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            throw new NotFoundException("Enrollment not found");

        Visit visit = new Visit();
        visit.id = UUID.randomUUID();
        visit.tenantId = enrollment.tenantId;
        visit.enrollmentId = enrollmentId;
        visit.visitType = req.visitType();
        visit.visitDate = req.visitDate();
        visit.status = req.status();
        visit.notes = req.notes();
        visit.createdAt = Instant.now();
        em.persist(visit);
        ledgerWriter.writeEntry(visit);
        return toView(visit);
    }

    @Override
    public List<VisitView> listVisits(UUID trialId, UUID siteId, UUID enrollmentId, String tenancyId) {
        return em.createNamedQuery("Visit.listByEnrollment", Visit.class).setParameter("enrollmentId", enrollmentId).setParameter("tenantId", tenancyId).getResultList().stream().map(DefaultClinicalVisitApi::toView).toList();
    }

    @Override
    public VisitView getVisit(UUID trialId, UUID siteId, UUID enrollmentId, UUID visitId, String tenancyId) {
        Visit visit = em.createNamedQuery("Visit.findByIdAndTenantId", Visit.class).setParameter("id", visitId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (visit == null) return null;
        return toView(visit);
    }

    @Override
    @Transactional
    public VisitView updateVisit(UUID trialId, UUID siteId, UUID enrollmentId, UUID visitId,
                                  UpdateVisitRequest req, String tenancyId) {
        Visit visit = em.createNamedQuery("Visit.findByIdAndTenantId", Visit.class).setParameter("id", visitId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (visit == null) return null;
        if (req.status() != null) visit.status = req.status();
        if (req.notes() != null) visit.notes = req.notes();
        return toView(visit);
    }

    private static VisitView toView(Visit v) {
        return new VisitView(v.id, v.enrollmentId, v.visitType, v.visitDate,
                             v.status, v.notes, v.createdAt);
    }
}
