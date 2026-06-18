package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.clinical.ledger.ProtocolAmendmentLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;

/**
 * Centralises tamper-evident ledger writes for the protocol amendment lifecycle.
 * Owns sequenceNumber computation via findLatestBySubjectId (ADR-0002).
 * Both writeProposalEntry and writeResolutionEntry write to the same subject chain.
 *
 * 21 CFR Part 312 §312.30 — protocol amendment review must be independently verifiable
 * from proposal through approval or rejection.
 */
@ApplicationScoped
public class ProtocolAmendmentLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeProposalEntry(ProtocolAmendment amendment) {
        ProtocolAmendmentLedgerEntry entry = base(amendment);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorRole = "amendment-proposer";
        entry.occurredAt = clock.instant();
        entry.status = amendment.status.name();
        entry.attach(ClinicalComplianceSupplement.protocolAmendment());
        ledgerEntryRepository.save(entry, "default");
    }

    public void writeResolutionEntry(ProtocolAmendment amendment) {
        ProtocolAmendmentLedgerEntry entry = base(amendment);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorRole = "amendment-advisor";
        entry.occurredAt = clock.instant();
        entry.status = amendment.status.name();
        entry.supervisorRecommendation = amendment.supervisorRecommendation != null
            ? amendment.supervisorRecommendation.name() : null;
        entry.attach(ClinicalComplianceSupplement.protocolAmendment());
        ledgerEntryRepository.save(entry, "default");
    }

    private ProtocolAmendmentLedgerEntry base(ProtocolAmendment amendment) {
        ProtocolAmendmentLedgerEntry entry = new ProtocolAmendmentLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = amendment.id;
        entry.sequenceNumber = nextSequenceNumber(amendment.id);
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.amendmentId = amendment.id;
        entry.trialId = amendment.trialId;
        entry.proposedChange = amendment.proposedChange;
        return entry;
    }

    private int nextSequenceNumber(UUID subjectId) {
        return ledgerEntryRepository.findLatestBySubjectId(subjectId, "default")
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
