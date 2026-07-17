package io.casehub.clinical.service;

import io.casehub.clinical.ledger.CbrRetrievalLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.neocortex.memory.cbr.CbrRetrievalTrace;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.platform.api.identity.ActorType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class CbrRetrievalLedgerWriter {

    @Inject LedgerEntryRepository ledgerEntryRepository;
    @Inject Clock clock;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void record(CbrRetrievalTrace trace, String explanation,
                       UUID subjectId, String actorId) {
        var entry = new CbrRetrievalLedgerEntry();
        entry.id = UUID.randomUUID();
        entry.subjectId = subjectId;
        entry.sequenceNumber = nextSequenceNumber(subjectId);
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = actorId;
        entry.actorType = ActorType.HUMAN;
        entry.actorRole = "cbr-retrieval-auditor";
        entry.occurredAt = clock.instant();
        entry.retrievalTraceId = trace.traceId();
        entry.queryDomain = trace.query().domain().name();
        entry.queryFeaturesSummary = summariseFeatures(trace.query().features());
        entry.retrievedCaseCount = trace.results().size();
        entry.topScore = trace.results().isEmpty() ? 0.0
            : trace.results().getFirst().score();
        entry.explanationText = explanation;
        entry.attach(ClinicalComplianceSupplement.cbrRetrieval());
        ledgerEntryRepository.save(entry, "default");
    }

    private int nextSequenceNumber(UUID subjectId) {
        return ledgerEntryRepository.findLatestBySubjectId(subjectId, "default")
                .map(e -> e.sequenceNumber + 1)
                .orElse(1);
    }

    private static String summariseFeatures(Map<String, FeatureValue> features) {
        if (features.isEmpty()) return "";
        return features.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue().toRawValue())
            .collect(Collectors.joining(","));
    }
}
