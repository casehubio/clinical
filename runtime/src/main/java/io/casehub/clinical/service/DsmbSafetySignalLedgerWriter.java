package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.DsmbSafetySignalEvent;
import io.casehub.clinical.ledger.DsmbSafetySignalLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class DsmbSafetySignalLedgerWriter {

    private static final Logger LOG = Logger.getLogger(DsmbSafetySignalLedgerWriter.class);

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onSignalDetected(@ObservesAsync DsmbSafetySignalEvent event) {
        DsmbSafetySignalLedgerEntry entry = new DsmbSafetySignalLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = event.trialId();
        entry.sequenceNumber = nextSequenceNumber(event.trialId());
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = ClinicalActors.CLINICAL_SERVICE;
        entry.actorType = ActorType.SYSTEM;
        entry.actorRole = "TrialSafetyAggregation";
        entry.occurredAt = clock.instant();
        entry.trialId = event.trialId();
        entry.signalType = event.signalType();
        entry.affectedSiteCount = event.affectedSites().size();
        entry.summary = event.summary();
        entry.attach(ClinicalComplianceSupplement.safetySignalDetection());
        ledgerEntryRepository.save(entry, "default");

        LOG.infof("Wrote DSMB safety signal ledger entry for trial %s, signal %s",
            event.trialId(), event.signalType());
    }

    private int nextSequenceNumber(UUID trialId) {
        return ledgerEntryRepository.findLatestBySubjectId(trialId, "default")
            .map(e -> e.sequenceNumber + 1)
            .orElse(1);
    }
}
