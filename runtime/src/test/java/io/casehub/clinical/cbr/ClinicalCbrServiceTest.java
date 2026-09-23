package io.casehub.clinical.cbr;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClinicalCbrServiceTest {

    private CbrRecordStore store;
    private ClinicalCbrService service;

    @BeforeEach
    void setUp() {
        store = mock(CbrRecordStore.class);
        service = new ClinicalCbrService(store,
            mock(io.casehub.neocortex.memory.cbr.ExplanationRenderer.class),
            mock(io.casehub.clinical.service.CbrRetrievalLedgerWriter.class),
            java.time.Clock.systemUTC());
    }

    @Test
    void storeIdempotent_erasesBeforeStore() {
        final var cbrCase = new CbrFeatureRecord("problem", "solution", "outcome", Confidence.unknown(0.9), Map.of(), null, null);
        final String caseType = "clinical-ae";
        final String entityId = "ae-123";
        final MemoryDomain domain = new MemoryDomain("clinical-ae");
        final String tenantId = "tenant-1";
        final String caseId = "case-123";

        when(store.store(cbrCase, caseType, entityId, domain, tenantId, caseId, io.casehub.platform.api.path.Path.root()))
            .thenReturn("cbr-id-123");

        final String result = service.storeIdempotent(cbrCase, caseType, entityId, domain, tenantId, caseId, io.casehub.platform.api.path.Path.root());

        assertThat(result).isEqualTo("cbr-id-123");

        final InOrder inOrder = inOrder(store);
        inOrder.verify(store).eraseEntity(entityId, tenantId);
        inOrder.verify(store).store(cbrCase, caseType, entityId, domain, tenantId, caseId, io.casehub.platform.api.path.Path.root());
    }

    @Test
    void retrieveSimilar_delegatesToStore() {
        final var query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-ae"),
            io.casehub.platform.api.path.Path.root(), "clinical-ae", FeatureValue.toFeatureMap(Map.of("grade", 3.0)), 5);
        final var expected = List.of(
            new CbrMatch<>(new CbrFeatureRecord("p1", "s1", "o1", Confidence.unknown(0.9), Map.of(), null, null), "clinical-ae", 0.95),
            new CbrMatch<>(new CbrFeatureRecord("p2", "s2", "o2", Confidence.unknown(0.8), Map.of(), null, null), "clinical-ae", 0.85)
        );

        when(store.retrieveSimilar(query, CbrFeatureRecord.class)).thenReturn(expected);

        final List<CbrMatch<CbrFeatureRecord>> result = service.retrieveSimilar(query, CbrFeatureRecord.class);

        assertThat(result).isEqualTo(expected);
        verify(store).retrieveSimilar(query, CbrFeatureRecord.class);
    }
}
