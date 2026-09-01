package io.casehub.clinical.cbr;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.CbrRetrievalTrace;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.platform.api.path.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalExplanationRendererTest {

    private ClinicalExplanationRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new ClinicalExplanationRenderer();
    }

    @Test
    void render_aeTrace_producesStructuredExplanation() {
        CbrQuery query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of("grade", FeatureValue.of(3)), 10)
            .withMinSimilarity(0.3);

        var trace = new CbrRetrievalTrace("trace-1", query, List.of(
            new CbrRetrievalTrace.TracedCase("case-1", 0.92, false, Map.of("grade", 1.0, "eventType", 0.95, "trialPhase", 0.80), Confidence.unknown(0.85), null, null, null),
            new CbrRetrievalTrace.TracedCase("case-2", 0.78, false, Map.of("grade", 0.8, "eventType", 0.70), Confidence.unknown(0.60), null, null, null)
        ), Instant.now());

        String result = renderer.render(trace);

        assertThat(result).contains("Adverse event precedent consultation");
        assertThat(result).contains("2 prior cases retrieved");
        assertThat(result).contains("score 0.92");
        assertThat(result).contains("grade=1.00");
        assertThat(result).contains("clinical-ae");
    }

    @Test
    void render_deviationTrace_usesDeviationLabel() {
        CbrQuery query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-deviation"),
            Path.root(), "clinical-deviation", Map.of(), 10);

        var trace = new CbrRetrievalTrace("trace-2", query, List.of(
            new CbrRetrievalTrace.TracedCase("case-1", 0.75, false, Map.of(), null, null, null, null)
        ), Instant.now());

        String result = renderer.render(trace);

        assertThat(result).contains("Protocol deviation precedent consultation");
        assertThat(result).contains("1 prior case retrieved");
    }

    @Test
    void render_amendmentTrace_usesAmendmentLabel() {
        CbrQuery query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-amendment"),
            Path.root(), "clinical-amendment", Map.of(), 10);

        var trace = new CbrRetrievalTrace("trace-3", query, List.of(), Instant.now());

        String result = renderer.render(trace);

        assertThat(result).contains("Protocol amendment precedent consultation");
        assertThat(result).contains("0 prior cases retrieved");
    }

    @Test
    void render_emptyResults_noNpeOrDivideByZero() {
        CbrQuery query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);

        var trace = new CbrRetrievalTrace("trace-4", query, List.of(), Instant.now());

        String result = renderer.render(trace);

        assertThat(result).contains("0 prior cases retrieved");
        assertThat(result).doesNotContain("Top precedent");
    }

    @Test
    void render_nullConfidence_handledGracefully() {
        CbrQuery query = CbrQuery.of("tenant-1", new MemoryDomain("clinical-ae"),
            Path.root(), "clinical-ae", Map.of(), 10);

        var trace = new CbrRetrievalTrace("trace-5", query, List.of(
            new CbrRetrievalTrace.TracedCase("case-1", 0.88, false, Map.of(), null, null, null, null),
            new CbrRetrievalTrace.TracedCase("case-2", 0.72, false, Map.of(), Confidence.unknown(0.90), null, null, null)
        ), Instant.now());

        String result = renderer.render(trace);

        assertThat(result).contains("1 has no recorded confidence");
    }
}
