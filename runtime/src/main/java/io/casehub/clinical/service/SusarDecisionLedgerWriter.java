package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.ledger.SusarDecisionLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes tamper-evident SUSAR oversight gate decision entries to the ledger.
 *
 * Each call records a gate outcome (APPROVED / REJECTED / EXPIRED) for a specific
 * adverse event, keyed by {@code enrollmentId} as the subjectId so the entry appears
 * in patient-scoped PROV-DM exports via {@code LedgerProvExportService.exportSubject()}.
 *
 * A {@link ClinicalComplianceSupplement#susarGateDecision()} is attached to every entry,
 * satisfying EU AI Act Art.12 and 21 CFR 312.32 requirements for traceable AI decisions.
 *
 * FDA IND / GCP requirement: SUSAR gate decisions must be independently verifiable with
 * named actor, timestamp, and regulatory reference.
 */
@ApplicationScoped
public class SusarDecisionLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeEntry(AdverseEvent ae, String gateOutcome, Instant decidedAt, String decidedBy) {
        SusarDecisionLedgerEntry entry = new SusarDecisionLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = ae.enrollmentId;
        entry.sequenceNumber = nextSequenceNumber(ae.enrollmentId);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = decidedBy != null ? decidedBy : ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = decidedBy != null ? ActorType.HUMAN : ActorType.SYSTEM;
        entry.actorRole = "SusarOversightGate";
        entry.occurredAt = clock.instant();
        entry.aeId = ae.id;
        entry.enrollmentId = ae.enrollmentId;
        entry.ctcaeGrade = ae.grade != null ? ae.grade.name() : null;
        entry.gateOutcome = gateOutcome;
        entry.decidedAt = decidedAt;
        entry.decidedBy = decidedBy;
        entry.attach(ClinicalComplianceSupplement.susarGateDecision());
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(UUID enrollmentId) {
        return ledgerEntryRepository.findLatestBySubjectId(enrollmentId, "default")
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
