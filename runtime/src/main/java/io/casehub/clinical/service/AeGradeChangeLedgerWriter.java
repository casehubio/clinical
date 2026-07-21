package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.ledger.AeGradeChangeLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class AeGradeChangeLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    public void writeGradeChangeEntry(AdverseEvent ae, CtcaeGrade previousGrade, String reason) {
        AeGradeChangeLedgerEntry entry = new AeGradeChangeLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = ae.id;
        entry.sequenceNumber = nextSequenceNumber(ae.id);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "AdverseEventRegrader";
        entry.occurredAt = clock.instant();
        entry.previousGrade = previousGrade != null ? previousGrade.name() : null;
        entry.newGrade = ae.grade.name();
        entry.reason = reason;
        entry.changedBy = ClinicalActors.CLINICAL_SERVICE;
        entry.attach(ClinicalComplianceSupplement.gradeChange());
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(UUID aeId) {
        return ledgerEntryRepository.findLatestBySubjectId(aeId, "default")
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
