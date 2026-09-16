package io.casehub.clinical.service;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class GdprErasureService {

    @Inject ConsentWithdrawalService consentWithdrawalService;
    @Inject
            jakarta.persistence.EntityManager em;


    @Transactional
    public int erasePatient(String patientId, String tenantId) {
        List<PatientEnrollment> enrollments = em.createQuery("SELECT e FROM PatientEnrollment e WHERE e.patientId = :patientId AND e.tenantId = :tenantId AND e.consentStatus != :status", PatientEnrollment.class)
                .setParameter("patientId", patientId).setParameter("tenantId", tenantId).setParameter("status", ConsentStatus.WITHDRAWN).getResultList();

        if (enrollments.isEmpty()) {
            throw new PatientNotFoundException(patientId);
        }

        int count = 0;
        for (PatientEnrollment enrollment : enrollments) {
            WithdrawalResult result = consentWithdrawalService.withdraw(enrollment.id, tenantId);
            if (result == WithdrawalResult.WITHDRAWN) {
                count++;
            }
        }
        return count;
    }
}
