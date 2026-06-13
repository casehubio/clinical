package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConsentWithdrawalServiceTest {

    @Inject ConsentWithdrawalService service;

    @Test
    void withdraw_sets_both_statuses_pseudonymizes_patientId_sets_withdrawnAt() {
        String originalPatientId = "patient-mrn-12345";
        UUID enrollmentId = persistEnrollment(originalPatientId);

        Response response = service.withdraw(enrollmentId, "default");

        assertThat(response.getStatus()).isEqualTo(204);
        PatientEnrollment updated = findEnrollment(enrollmentId);
        assertThat(updated.consentStatus).isEqualTo(ConsentStatus.WITHDRAWN);
        assertThat(updated.enrollmentStatus).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(updated.patientId).startsWith("erased-");
        assertThat(updated.patientId).doesNotContain(originalPatientId);
        assertThat(updated.withdrawnAt).isNotNull();
    }

    @Test
    void withdraw_returns_409_if_already_withdrawn() {
        UUID enrollmentId = persistEnrollment("patient-xyz");
        setWithdrawn(enrollmentId);

        Response response = service.withdraw(enrollmentId, "default");

        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    void withdraw_returns_404_for_unknown_enrollment() {
        Response response = service.withdraw(UUID.randomUUID(), "default");
        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Transactional
    UUID persistEnrollment(String patientId) {
        PatientEnrollment e = new PatientEnrollment();
        e.id = UUID.randomUUID();
        e.siteId = UUID.randomUUID();
        e.patientId = patientId;
        e.tenantId = "default";
        e.consentStatus = ConsentStatus.PENDING;
        e.enrollmentStatus = EnrollmentStatus.CANDIDATE;
        e.persist();
        return e.id;
    }

    @Transactional
    void setWithdrawn(UUID enrollmentId) {
        PatientEnrollment e = PatientEnrollment.findById(enrollmentId);
        e.consentStatus = ConsentStatus.WITHDRAWN;
    }

    @Transactional
    PatientEnrollment findEnrollment(UUID enrollmentId) {
        return PatientEnrollment.findById(enrollmentId);
    }
}
