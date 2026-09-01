package io.casehub.clinical.cbr;

import io.casehub.clinical.service.CbrRetrievalLedgerWriter;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.*;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ClinicalCbrServiceAuditTest {

    private CbrCaseMemoryStore store;
    private ExplanationRenderer renderer;
    private CbrRetrievalLedgerWriter writer;
    private ClinicalCbrService service;

    @BeforeEach
    void setUp() {
        store = mock(CbrCaseMemoryStore.class);
        renderer = mock(ExplanationRenderer.class);
        writer = mock(CbrRetrievalLedgerWriter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZoneOffset.UTC);
        service = new ClinicalCbrService(store, renderer, writer, clock);
    }

    @Test
    void retrieveWithAudit_callsRetrieveThenRenderThenWrite() {
        CbrQuery query = CbrQuery.of("t1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);
        var scored = new ScoredCbrCase<>(mock(PlanCbrCase.class), "c1", 0.9);
        when(store.retrieveSimilar(query, PlanCbrCase.class)).thenReturn(List.of(scored));
        when(renderer.render(any())).thenReturn("explanation-text");

        UUID subjectId = UUID.randomUUID();
        var result = service.retrieveWithAudit(query, PlanCbrCase.class, subjectId, "actor-1");

        assertThat(result.cases()).hasSize(1);
        assertThat(result.traceId()).isNotNull();
        assertThat(result.explanation()).isEqualTo("explanation-text");

        verify(renderer).render(any(CbrRetrievalTrace.class));
        verify(writer).record(any(CbrRetrievalTrace.class), eq("explanation-text"),
            eq(subjectId), eq("actor-1"));
    }

    @Test
    void retrieveWithAudit_renderThrows_explanationNullButLedgerWritten() {
        CbrQuery query = CbrQuery.of("t1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);
        when(store.retrieveSimilar(query, PlanCbrCase.class)).thenReturn(List.of());
        when(renderer.render(any())).thenThrow(new RuntimeException("render failed"));

        var result = service.retrieveWithAudit(query, PlanCbrCase.class, UUID.randomUUID(), "actor-1");

        assertThat(result.explanation()).isNull();
        verify(writer).record(any(), isNull(), any(), eq("actor-1"));
    }

    @Test
    void retrieveWithAudit_writerThrows_propagatesToCaller() {
        CbrQuery query = CbrQuery.of("t1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);
        when(store.retrieveSimilar(query, PlanCbrCase.class)).thenReturn(List.of());
        when(renderer.render(any())).thenReturn("text");
        doThrow(new RuntimeException("ledger write failed")).when(writer).record(any(), any(), any(), any());

        assertThatThrownBy(() ->
            service.retrieveWithAudit(query, PlanCbrCase.class, UUID.randomUUID(), "actor-1"))
            .hasMessageContaining("ledger write failed");
    }

    @Test
    void retrieveWithAudit_emptyResults_stillWritesLedgerEntry() {
        CbrQuery query = CbrQuery.of("t1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);
        when(store.retrieveSimilar(query, PlanCbrCase.class)).thenReturn(List.of());
        when(renderer.render(any())).thenReturn("0 cases");

        service.retrieveWithAudit(query, PlanCbrCase.class, UUID.randomUUID(), "actor-1");

        verify(writer).record(any(), eq("0 cases"), any(), any());
    }

    @Test
    void retrieveSimilar_unchanged_noAudit() {
        CbrQuery query = CbrQuery.of("t1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);
        when(store.retrieveSimilar(query, PlanCbrCase.class)).thenReturn(List.of());

        service.retrieveSimilar(query, PlanCbrCase.class);

        verifyNoInteractions(renderer);
        verifyNoInteractions(writer);
    }
}
