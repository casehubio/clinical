package io.casehub.clinical.service;

import io.casehub.clinical.ledger.CbrRetrievalLedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.CbrRetrievalTrace;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CbrRetrievalLedgerWriterTest {

    private LedgerEntryRepository repository;
    private CbrRetrievalLedgerWriter writer;
    private Clock clock;

    @BeforeEach
    void setUp() {
        repository = mock(LedgerEntryRepository.class);
        clock = Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZoneOffset.UTC);
        writer = new CbrRetrievalLedgerWriter();
        writer.ledgerEntryRepository = repository;
        writer.clock = clock;

        when(repository.findLatestBySubjectId(any(), eq("default"))).thenReturn(Optional.empty());
    }

    @Test
    void record_writesEntryWithCorrectFields() {
        CbrQuery query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of("grade", FeatureValue.of(3)), 10);

        var trace = new CbrRetrievalTrace("trace-abc", query, List.of(
            new CbrRetrievalTrace.TracedCase("case-1", 0.92, false, Map.of(), 0.85, null, null, null)
        ), Instant.now());

        UUID subjectId = UUID.randomUUID();
        writer.record(trace, "Explanation text", subjectId, "clinician-1");

        ArgumentCaptor<CbrRetrievalLedgerEntry> captor = ArgumentCaptor.forClass(CbrRetrievalLedgerEntry.class);
        verify(repository).save(captor.capture(), eq("default"));

        CbrRetrievalLedgerEntry entry = captor.getValue();
        assertThat(entry.retrievalTraceId).isEqualTo("trace-abc");
        assertThat(entry.queryDomain).isEqualTo("clinical-ae");
        assertThat(entry.queryFeaturesSummary).contains("grade=");
        assertThat(entry.retrievedCaseCount).isEqualTo(1);
        assertThat(entry.topScore).isEqualTo(0.92);
        assertThat(entry.explanationText).isEqualTo("Explanation text");
        assertThat(entry.subjectId).isEqualTo(subjectId);
        assertThat(entry.actorId).isEqualTo("clinician-1");
        assertThat(entry.actorType).isEqualTo(ActorType.HUMAN);
        assertThat(entry.actorRole).isEqualTo("cbr-retrieval-auditor");
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.occurredAt).isEqualTo(clock.instant());
        assertThat(entry.sequenceNumber).isEqualTo(1);
    }

    @Test
    void record_nullExplanation_writesEntryWithNullExplanation() {
        CbrQuery query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);

        var trace = new CbrRetrievalTrace("trace-null", query, List.of(), Instant.now());

        writer.record(trace, null, UUID.randomUUID(), "clinician-1");

        ArgumentCaptor<CbrRetrievalLedgerEntry> captor = ArgumentCaptor.forClass(CbrRetrievalLedgerEntry.class);
        verify(repository).save(captor.capture(), eq("default"));

        assertThat(captor.getValue().explanationText).isNull();
    }

    @Test
    void record_emptyResults_topScoreIsZero() {
        CbrQuery query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);

        var trace = new CbrRetrievalTrace("trace-empty", query, List.of(), Instant.now());

        writer.record(trace, "No cases found", UUID.randomUUID(), "clinician-1");

        ArgumentCaptor<CbrRetrievalLedgerEntry> captor = ArgumentCaptor.forClass(CbrRetrievalLedgerEntry.class);
        verify(repository).save(captor.capture(), eq("default"));

        assertThat(captor.getValue().retrievedCaseCount).isZero();
        assertThat(captor.getValue().topScore).isEqualTo(0.0);
    }

    @Test
    void record_attachesComplianceSupplement() {
        CbrQuery query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);

        var trace = new CbrRetrievalTrace("trace-cs", query, List.of(), Instant.now());

        writer.record(trace, "text", UUID.randomUUID(), "clinician-1");

        ArgumentCaptor<CbrRetrievalLedgerEntry> captor = ArgumentCaptor.forClass(CbrRetrievalLedgerEntry.class);
        verify(repository).save(captor.capture(), eq("default"));

        assertThat(captor.getValue().supplements).isNotEmpty();
    }


}
