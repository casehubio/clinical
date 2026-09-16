package io.casehub.clinical.service.api;

import io.casehub.clinical.api.spi.ClinicalVitalApi;
import io.casehub.clinical.api.view.RecordVitalSignRequest;
import io.casehub.clinical.api.view.VitalSignView;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.VitalSign;
import io.casehub.clinical.service.VitalSignLedgerWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalVitalApi implements ClinicalVitalApi {

    @Inject VitalSignLedgerWriter ledgerWriter;
    @Inject
            jakarta.persistence.EntityManager em;


    @Override
    @Transactional
    public VitalSignView recordVital(UUID trialId, UUID siteId, UUID enrollmentId,
                                      RecordVitalSignRequest req, String tenancyId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            throw new NotFoundException("Enrollment not found");

        VitalSign vital = new VitalSign();
        vital.id = UUID.randomUUID();
        vital.tenantId = enrollment.tenantId;
        vital.enrollmentId = enrollmentId;
        vital.visitId = req.visitId();
        vital.type = req.type();
        vital.value = req.value();
        vital.unit = req.unit();
        vital.measuredAt = req.measuredAt();
        vital.createdAt = Instant.now();
        em.persist(vital);
        ledgerWriter.writeEntry(vital);
        return toView(vital);
    }

    @Override
    public List<VitalSignView> listVitals(UUID trialId, UUID siteId, UUID enrollmentId, String tenancyId) {
        return em.createNamedQuery("VitalSign.listByEnrollment", VitalSign.class).setParameter("enrollmentId", enrollmentId).setParameter("tenantId", tenancyId).getResultList().stream().map(DefaultClinicalVitalApi::toView).toList();
    }

    @Override
    public VitalSignView getVital(UUID trialId, UUID siteId, UUID enrollmentId, UUID vitalId, String tenancyId) {
        VitalSign vital = em.createNamedQuery("VitalSign.findByIdAndTenantId", VitalSign.class).setParameter("id", vitalId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (vital == null) return null;
        return toView(vital);
    }

    private static VitalSignView toView(VitalSign v) {
        return new VitalSignView(v.id, v.enrollmentId, v.visitId, v.type,
                                  v.value, v.unit, v.measuredAt, v.createdAt);
    }
}
