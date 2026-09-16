package io.casehub.clinical.service.api;

import io.casehub.clinical.api.spi.ClinicalLabResultApi;
import io.casehub.clinical.api.view.LabResultView;
import io.casehub.clinical.api.view.RecordLabResultRequest;
import io.casehub.clinical.entity.LabResult;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.service.LabResultLedgerWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalLabResultApi implements ClinicalLabResultApi {

    @Inject LabResultLedgerWriter ledgerWriter;
    @Inject
            EntityManager         em;


    @Override
    @Transactional
    public LabResultView recordLabResult(UUID trialId, UUID siteId, UUID enrollmentId,
                                          RecordLabResultRequest req, String tenancyId) {
        PatientEnrollment enrollment = em.createNamedQuery("PatientEnrollment.findByIdAndTenantId", PatientEnrollment.class).setParameter("id", enrollmentId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (enrollment == null || !enrollment.siteId.equals(siteId))
            throw new NotFoundException("Enrollment not found");

        LabResult lab = new LabResult();
        lab.id = UUID.randomUUID();
        lab.tenantId = enrollment.tenantId;
        lab.enrollmentId = enrollmentId;
        lab.visitId = req.visitId();
        lab.testName = req.testName();
        lab.value = req.value();
        lab.unit = req.unit();
        lab.referenceRangeLow = req.referenceRangeLow();
        lab.referenceRangeHigh = req.referenceRangeHigh();
        lab.abnormalFlag = req.abnormalFlag();
        lab.specimenType = req.specimenType();
        lab.performingLab = req.performingLab();
        lab.collectedAt = req.collectedAt();
        lab.createdAt = Instant.now();
        em.persist(lab);
        ledgerWriter.writeEntry(lab);
        return toView(lab);
    }

    @Override
    public List<LabResultView> listLabResults(UUID trialId, UUID siteId, UUID enrollmentId, String tenancyId) {
        return em.createNamedQuery("LabResult.listByEnrollment", LabResult.class).setParameter("enrollmentId", enrollmentId).setParameter("tenantId", tenancyId).getResultList().stream().map(DefaultClinicalLabResultApi::toView).toList();
    }

    @Override
    public LabResultView getLabResult(UUID trialId, UUID siteId, UUID enrollmentId, UUID labId, String tenancyId) {
        LabResult lab = em.createNamedQuery("LabResult.findByIdAndTenantId", LabResult.class).setParameter("id", labId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (lab == null) return null;
        return toView(lab);
    }

    private static LabResultView toView(LabResult l) {
        return new LabResultView(l.id, l.enrollmentId, l.visitId, l.testName,
                                  l.value, l.unit, l.referenceRangeLow, l.referenceRangeHigh,
                                  l.abnormalFlag, l.specimenType, l.performingLab,
                                  l.collectedAt, l.createdAt);
    }
}
