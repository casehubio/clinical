package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GdprErasureServiceTest {

    @Inject GdprErasureService erasureService;

    @Test
    void erasePatient_withdraws_single_enrollment() {
        String patientId = "GDPR-PAT-" + UUID.randomUUID();
        UUID enrollmentId = persistEnrollment(patientId);

        int count = erasureService.erasePatient(patientId, "default");
        assertThat(count).isEqualTo(1);

        PatientEnrollment updated = findEnrollment(enrollmentId);
        assertThat(updated.consentStatus).isEqualTo(ConsentStatus.WITHDRAWN);
        assertThat(updated.patientId).startsWith("erased-");
    }

    @Test
    void erasePatient_withdraws_multiple_enrollments_across_sites() {
        String patientId = "GDPR-MULTI-" + UUID.randomUUID();
        UUID e1 = persistEnrollment(patientId);
        UUID e2 = persistEnrollment(patientId);
        UUID e3 = persistEnrollment(patientId);

        int count = erasureService.erasePatient(patientId, "default");
        assertThat(count).isEqualTo(3);

        assertThat(findEnrollment(e1).consentStatus).isEqualTo(ConsentStatus.WITHDRAWN);
        assertThat(findEnrollment(e2).consentStatus).isEqualTo(ConsentStatus.WITHDRAWN);
        assertThat(findEnrollment(e3).consentStatus).isEqualTo(ConsentStatus.WITHDRAWN);
    }

    @Test
    void erasePatient_throws_when_no_enrollments_found() {
        assertThatThrownBy(() -> erasureService.erasePatient("NONEXISTENT", "default"))
                .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void erasePatient_skips_already_withdrawn_enrollments() {
        String patientId = "GDPR-PARTIAL-" + UUID.randomUUID();
        UUID active = persistEnrollment(patientId);
        UUID withdrawn = persistEnrollment(patientId);
        setWithdrawn(withdrawn);

        int count = erasureService.erasePatient(patientId, "default");
        assertThat(count).isEqualTo(1);
        assertThat(findEnrollment(active).consentStatus).isEqualTo(ConsentStatus.WITHDRAWN);
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
    void setWithdrawn(UUID id) {
        PatientEnrollment e = PatientEnrollment.findById(id);
        e.consentStatus = ConsentStatus.WITHDRAWN;
        e.patientId = "erased-" + UUID.randomUUID();
    }

    @Transactional
    PatientEnrollment findEnrollment(UUID id) {
        return PatientEnrollment.findById(id);
    }
}
