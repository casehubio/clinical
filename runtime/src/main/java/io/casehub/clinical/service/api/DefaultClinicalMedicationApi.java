package io.casehub.clinical.service.api;

import io.casehub.clinical.api.spi.ClinicalMedicationApi;
import io.casehub.clinical.api.view.MedicationView;
import io.casehub.clinical.api.view.RecordMedicationRequest;
import io.casehub.clinical.api.view.UpdateMedicationRequest;
import io.casehub.clinical.entity.ConcomitantMedication;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.service.ConcomitantMedicationLedgerWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalMedicationApi implements ClinicalMedicationApi {
    @Inject
    EntityManager em;


    @Inject ConcomitantMedicationLedgerWriter ledgerWriter;

    @Override
    @Transactional
    public MedicationView recordMedication(UUID trialId, UUID siteId, UUID enrollmentId,
                                            RecordMedicationRequest req, String tenancyId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            throw new NotFoundException("Enrollment not found");

        ConcomitantMedication med = new ConcomitantMedication();
        med.id = UUID.randomUUID();
        med.tenantId = enrollment.tenantId;
        med.enrollmentId = enrollmentId;
        med.medicationName = req.medicationName();
        med.indication = req.indication();
        med.dose = req.dose();
        med.unit = req.unit();
        med.route = req.route();
        med.frequency = req.frequency();
        med.startDate = req.startDate();
        med.endDate = req.endDate();
        med.ongoing = req.ongoing();
        med.createdAt = Instant.now();
        em.persist(med);
        ledgerWriter.writeEntry(med);
        return toView(med);
    }

    @Override
    public List<MedicationView> listMedications(UUID trialId, UUID siteId, UUID enrollmentId, String tenancyId) {
        return em.createNamedQuery("ConcomitantMedication.listByEnrollment", ConcomitantMedication.class).setParameter("enrollmentId", enrollmentId).setParameter("tenantId", tenancyId).getResultList().stream().map(DefaultClinicalMedicationApi::toView).toList();
    }

    @Override
    public MedicationView getMedication(UUID trialId, UUID siteId, UUID enrollmentId, UUID medId, String tenancyId) {
        ConcomitantMedication med = em.createNamedQuery("ConcomitantMedication.findByIdAndTenantId", ConcomitantMedication.class).setParameter("id", medId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (med == null) return null;
        return toView(med);
    }

    @Override
    @Transactional
    public MedicationView updateMedication(UUID trialId, UUID siteId, UUID enrollmentId, UUID medId,
                                            UpdateMedicationRequest req, String tenancyId) {
        ConcomitantMedication med = em.createNamedQuery("ConcomitantMedication.findByIdAndTenantId", ConcomitantMedication.class).setParameter("id", medId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (med == null) return null;
        if (req.endDate() != null) med.endDate = req.endDate();
        if (req.ongoing() != null) med.ongoing = req.ongoing();
        return toView(med);
    }

    private static MedicationView toView(ConcomitantMedication m) {
        return new MedicationView(m.id, m.enrollmentId, m.medicationName, m.indication,
                                   m.dose, m.unit, m.route, m.frequency,
                                   m.startDate, m.endDate, m.ongoing, m.createdAt);
    }
}
