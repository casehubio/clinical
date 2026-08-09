package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.ledger.DsmbBatchSignalNotificationLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class DsmbBatchSignalNotificationLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeSuccess(UUID trialId, String signalType, UUID workItemId,
                             String connectorId, String destination) {
        writeEntry(trialId, signalType, workItemId, connectorId, destination, true, null);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void writeFailure(UUID trialId, String signalType, UUID workItemId,
                             String connectorId, String destination, String failureReason) {
        writeEntry(trialId, signalType, workItemId, connectorId, destination, false, failureReason);
    }

    private void writeEntry(UUID trialId, String signalType, UUID workItemId,
                            String connectorId, String destination,
                            boolean delivered, String failureReason) {
        var entry = new DsmbBatchSignalNotificationLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = trialId;
        entry.sequenceNumber = ledgerEntryRepository.findLatestBySubjectId(trialId, "default")
            .map(e -> e.sequenceNumber + 1).orElse(1);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "DsmbBatchSignalNotification";
        entry.occurredAt = clock.instant();
        entry.trialId = trialId;
        entry.signalType = signalType;
        entry.workItemId = workItemId;
        entry.connectorId = connectorId;
        entry.destination = destination;
        entry.delivered = delivered;
        entry.failureReason = failureReason;
        entry.notifiedAt = clock.instant();
        entry.attach(ClinicalComplianceSupplement.safetySignalDetection());
        ledgerEntryRepository.save(entry, "default");
    }
}
