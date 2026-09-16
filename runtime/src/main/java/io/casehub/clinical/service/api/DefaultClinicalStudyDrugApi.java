package io.casehub.clinical.service.api;

import io.casehub.clinical.api.spi.ClinicalStudyDrugApi;
import io.casehub.clinical.api.view.RecordDrugAdminRequest;
import io.casehub.clinical.api.view.StudyDrugView;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.StudyDrugAdministration;
import io.casehub.clinical.service.StudyDrugLedgerWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalStudyDrugApi implements ClinicalStudyDrugApi {

    @Inject StudyDrugLedgerWriter ledgerWriter;
    @Inject
            jakarta.persistence.EntityManager em;


    @Override
    @Transactional
    public StudyDrugView recordDrugAdmin(UUID trialId, UUID siteId, UUID enrollmentId,
                                          RecordDrugAdminRequest req, String tenancyId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            throw new NotFoundException("Enrollment not found");

        StudyDrugAdministration drug = new StudyDrugAdministration();
        drug.id = UUID.randomUUID();
        drug.tenantId = enrollment.tenantId;
        drug.enrollmentId = enrollmentId;
        drug.drugName = req.drugName();
        drug.dose = req.dose();
        drug.unit = req.unit();
        drug.route = req.route();
        drug.administeredAt = req.administeredAt();
        drug.administeredBy = req.administeredBy();
        drug.batchNumber = req.batchNumber();
        drug.status = req.status();
        drug.createdAt = Instant.now();
        em.persist(drug);
        ledgerWriter.writeEntry(drug);
        return toView(drug);
    }

    @Override
    public List<StudyDrugView> listDrugAdmins(UUID trialId, UUID siteId, UUID enrollmentId, String tenancyId) {
        return em.createNamedQuery("StudyDrugAdministration.listByEnrollment", StudyDrugAdministration.class).setParameter("enrollmentId", enrollmentId).setParameter("tenantId", tenancyId).getResultList().stream().map(DefaultClinicalStudyDrugApi::toView).toList();
    }

    @Override
    public StudyDrugView getDrugAdmin(UUID trialId, UUID siteId, UUID enrollmentId, UUID adminId, String tenancyId) {
        StudyDrugAdministration drug = em.createNamedQuery("StudyDrugAdministration.findByIdAndTenantId", StudyDrugAdministration.class).setParameter("id", adminId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (drug == null) return null;
        return toView(drug);
    }

    private static StudyDrugView toView(StudyDrugAdministration d) {
        return new StudyDrugView(d.id, d.enrollmentId, d.drugName, d.dose, d.unit,
                                  d.route, d.administeredAt, d.administeredBy,
                                  d.batchNumber, d.status, d.createdAt);
    }
}
