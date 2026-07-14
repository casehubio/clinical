package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.model.EligibilityScreeningResult;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.ledger.EligibilityScreeningLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Centralises tamper-evident ledger writes for the eligibility screening lifecycle.
 * Owns sequenceNumber computation via findLatestBySubjectId (ADR-0002).
 * writeResolutionEntry() added when IRB completion listener lands (out of scope here).
 */
@ApplicationScoped
public class EligibilityScreeningLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeScreeningEntry(PatientEnrollment enrollment,
                                    List<CriterionResult> criteria,
                                    EligibilityScreeningResult result) {
        EligibilityScreeningLedgerEntry entry = new EligibilityScreeningLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = enrollment.id;
        entry.sequenceNumber = nextSequenceNumber(enrollment.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "eligibility-screener";
        entry.occurredAt = clock.instant();
        entry.enrollmentId = enrollment.id;
        entry.screeningResult = result.name();
        entry.criteriaCount = criteria.size();
        entry.marginalCount = (int) criteria.stream().filter(CriterionResult::marginal).count();
        entry.attach(ClinicalComplianceSupplement.eligibilityScreening());
        ledgerEntryRepository.save(entry, "default");
    }

    public void writeResolutionEntry(PatientEnrollment enrollment,
                                     String resolutionOutcome,
                                     String resolvedBy) {
        EligibilityScreeningLedgerEntry entry = new EligibilityScreeningLedgerEntry();
        entry.id              = UUID.randomUUID();
        entry.subjectId       = enrollment.id;
        entry.sequenceNumber  = nextSequenceNumber(enrollment.id);
        entry.entryType       = LedgerEntryType.EVENT;
        entry.actorId         = resolvedBy;
        entry.actorType       = ActorType.HUMAN;
        entry.actorRole       = "eligibility-resolver";
        entry.occurredAt      = clock.instant();
        entry.enrollmentId    = enrollment.id;
        entry.screeningResult = resolutionOutcome;
        entry.criteriaCount   = 0;
        entry.marginalCount   = 0;
        entry.attach(ClinicalComplianceSupplement.eligibilityScreening());
        ledgerEntryRepository.save(entry, "default");
    }


    private int nextSequenceNumber(UUID subjectId) {
        return ledgerEntryRepository.findLatestBySubjectId(subjectId, "default")
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
