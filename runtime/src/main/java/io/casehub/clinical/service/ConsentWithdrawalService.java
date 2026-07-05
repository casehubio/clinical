package io.casehub.clinical.service;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.ledger.ConsentWithdrawalLedgerEntry;
import io.casehub.ledger.api.model.ErasureReason;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.privacy.LedgerErasureService;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.neocortex.memory.CaseMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * GDPR Art.17 consent withdrawal — pseudonymizes patient identity across
 * the domain entity, ledger audit trail, and case memory store.
 *
 * <p>Runs in a single XA transaction spanning both datasources (default for
 * PatientEnrollment, qhorus for ConsentWithdrawalLedgerEntry). XA is configured
 * in application.properties for both datasources.
 *
 * <p>Sequence:
 * <ol>
 *   <li>Load PatientEnrollment by enrollmentId + tenantId (404 if not found)</li>
 *   <li>Guard: if already WITHDRAWN, return 409</li>
 *   <li>Set consentStatus=WITHDRAWN, enrollmentStatus=WITHDRAWN, withdrawnAt=now,
 *       patientId="erased-{random-UUID}" (pseudonymization of PII field)</li>
 *   <li>Write ConsentWithdrawalLedgerEntry (actorId=enrollmentId, tokenized at save)</li>
 *   <li>Call LedgerErasureService.erase(enrollmentId) to pseudonymize actorId
 *       tokenization in all ledger entries</li>
 *   <li>Set ledgerEntriesAffected on the entry (JPA flush-on-commit updates the row)</li>
 *   <li>Erase patient memories from CaseMemoryStore (best-effort, WARN on failure)</li>
 *   <li>Return 204 No Content</li>
 * </ol>
 */
@ApplicationScoped
public class ConsentWithdrawalService {

    private static final Logger LOG = Logger.getLogger(ConsentWithdrawalService.class);

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject LedgerErasureService ledgerErasureService;
    @Inject CaseMemoryStore memoryStore;
    @Inject Clock clock;

    @Transactional
    public WithdrawalResult withdraw(UUID enrollmentId, String tenantId) {
        PatientEnrollment enrollment = PatientEnrollment.find(
                "id = ?1 AND tenantId = ?2", enrollmentId, tenantId).firstResult();
        if (enrollment == null) {
            throw new PatientEnrollmentNotFoundException(enrollmentId);
        }
        if (enrollment.consentStatus == ConsentStatus.WITHDRAWN) {
            return WithdrawalResult.ALREADY_WITHDRAWN;
        }

        Instant now = clock.instant();
        enrollment.consentStatus = ConsentStatus.WITHDRAWN;
        enrollment.enrollmentStatus = EnrollmentStatus.WITHDRAWN;
        enrollment.withdrawnAt = now;
        enrollment.patientId = "erased-" + UUID.randomUUID();
        enrollment.persist();

        // Write tamper-evident withdrawal record — actorId=enrollmentId, tokenized by
        // LedgerIdentityEnforcementListener at persist time, then pseudonymized by erase().
        ConsentWithdrawalLedgerEntry entry = new ConsentWithdrawalLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = enrollmentId;
        entry.sequenceNumber = nextSequenceNumber(enrollmentId);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = enrollmentId.toString();
        entry.actorType = ActorType.HUMAN;
        entry.actorRole = "PatientWithdrawal";
        entry.occurredAt = now;
        entry.enrollmentId = enrollmentId;
        entry.withdrawnAt = now;
        ledgerEntryRepository.save(entry, "default");

        // Pseudonymize the actorId tokenization across all ledger entries for this patient.
        // erase() returns the count of entries whose tokenized actorId matched — this
        // includes the entry we just wrote, so ledgerEntriesAffected >= 1 when tokenisation
        // is enabled. Setting the field here is safe: the JPA entity is still in the
        // persistence context and will be flushed at transaction commit.
        LedgerErasureService.ErasureResult erasureResult =
                ledgerErasureService.erase(enrollmentId.toString(), ErasureReason.GDPR_ART_17_REQUEST);
        LOG.infof("ConsentWithdrawalService: erased enrollmentId=%s mappingFound=%s affected=%d",
                enrollmentId, erasureResult.mappingFound(), erasureResult.affectedEntryCount());
        entry.ledgerEntriesAffected = erasureResult.affectedEntryCount();
        entry.receiptEntryId = erasureResult.receiptEntryId().orElse(null);

        // Erase all patient memories (best-effort — memory store failure does not
        // roll back the GDPR erasure already committed in the ledger).
        try {
            int memoriesErased = memoryStore.eraseEntity("patient:" + enrollmentId, tenantId);
            entry.memoriesErased = memoriesErased > 0;
            LOG.infof("ConsentWithdrawalService: erased %d patient memories for enrollmentId=%s",
                    memoriesErased, enrollmentId);
        } catch (Exception e) {
            LOG.warnf(e,
                    "ConsentWithdrawalService: memory erasure failed for enrollmentId=%s — ignored",
                    enrollmentId);
        }

        return WithdrawalResult.WITHDRAWN;
    }

    private int nextSequenceNumber(UUID enrollmentId) {
        return ledgerEntryRepository.findLatestBySubjectId(enrollmentId, "default")
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }
}
