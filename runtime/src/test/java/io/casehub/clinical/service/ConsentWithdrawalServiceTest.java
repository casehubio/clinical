package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.ledger.ConsentWithdrawalLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ConsentWithdrawalServiceTest {

    @Inject ConsentWithdrawalService service;
    @Inject LedgerEntryRepository ledgerEntryRepository;

    @Test
    void withdraw_sets_both_statuses_pseudonymizes_patientId_sets_withdrawnAt() {
        String originalPatientId = "patient-mrn-12345";
        UUID enrollmentId = persistEnrollment(originalPatientId);

        service.withdraw(enrollmentId, "default");

        PatientEnrollment updated = findEnrollment(enrollmentId);
        assertThat(updated.consentStatus).isEqualTo(ConsentStatus.WITHDRAWN);
        assertThat(updated.enrollmentStatus).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(updated.patientId).startsWith("erased-");
        assertThat(updated.patientId).doesNotContain(originalPatientId);
        assertThat(updated.withdrawnAt).isNotNull();

        // Ledger entry written for tamper-evident audit trail (GDPR Art.17)
        var entry = ledgerEntryRepository.findLatestBySubjectId(enrollmentId, "default");
        assertThat(entry).isPresent()
                .get().isInstanceOf(ConsentWithdrawalLedgerEntry.class);
        var withdrawalEntry = (ConsentWithdrawalLedgerEntry) entry.get();
        assertThat(withdrawalEntry.enrollmentId).isEqualTo(enrollmentId);
        assertThat(withdrawalEntry.withdrawnAt).isNotNull();
    }

    @Test
    void withdraw_throws_on_already_withdrawn() {
        UUID enrollmentId = persistEnrollment("patient-xyz");
        setWithdrawn(enrollmentId);

        assertThatThrownBy(() -> service.withdraw(enrollmentId, "default"))
                .isInstanceOf(ConsentAlreadyWithdrawnException.class);
    }

    @Test
    void withdraw_throws_on_unknown_enrollment() {
        assertThatThrownBy(() -> service.withdraw(UUID.randomUUID(), "default"))
                .isInstanceOf(PatientEnrollmentNotFoundException.class);
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
