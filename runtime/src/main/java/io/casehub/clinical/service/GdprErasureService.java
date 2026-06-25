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

    @Transactional
    public int erasePatient(String patientId, String tenantId) {
        List<PatientEnrollment> enrollments = PatientEnrollment.find(
                "patientId = ?1 AND tenantId = ?2 AND consentStatus != ?3",
                patientId, tenantId, ConsentStatus.WITHDRAWN).list();

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
