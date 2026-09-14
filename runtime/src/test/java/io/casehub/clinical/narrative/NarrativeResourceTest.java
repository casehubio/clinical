package io.casehub.clinical.narrative;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.narrative.DecisionNarrative;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NarrativeResourceTest {

    private EventStreamBus<DecisionNarrative> narrativeBus;
    private NarrativeResource resource;

    @BeforeEach
    void setUp() {
        narrativeBus = new EventStreamBus<>();
        var pipeline = mock(DecisionNarrativePipeline.class);
        when(pipeline.narrativeBus()).thenReturn(narrativeBus);
        resource = new NarrativeResource(pipeline);
    }

    @Test
    void returnsEmptyWhenNoCaseData() {
        var result = resource.get("case-unknown");
        assertThat(result).isEmpty();
    }

    @Test
    void cachesNarrative() {
        var narrative = new DecisionNarrative("case-1", List.of("susar-assessment"),
                "SUSAR criteria evaluated — adverse event classified as serious and unexpected",
                List.of("ae-record-1"), 0.90, Instant.now());
        narrativeBus.publish(new LevelEvent<>(narrative, Instant.now().toEpochMilli(),
                new EventLevel("decision-narratives", 2), null));

        var result = resource.get("case-1");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().explanation()).contains("SUSAR");
    }

    @Test
    void accumulatesMultipleNarrativesPerCase() {
        var n1 = new DecisionNarrative("case-1", List.of("susar-assessment"),
                "SUSAR evaluation", List.of(), 0.85, Instant.now());
        var n2 = new DecisionNarrative("case-1", List.of("safety-review"),
                "Safety review complete", List.of(), 0.90, Instant.now());
        var level = new EventLevel("decision-narratives", 2);
        narrativeBus.publish(new LevelEvent<>(n1, Instant.now().toEpochMilli(), level, null));
        narrativeBus.publish(new LevelEvent<>(n2, Instant.now().toEpochMilli(), level, null));

        var result = resource.get("case-1");
        assertThat(result).hasSize(2);
    }

    @Test
    void isolatesNarrativesByCase() {
        var n1 = new DecisionNarrative("case-1", List.of("step"), "Case 1", List.of(), 0.8, Instant.now());
        var n2 = new DecisionNarrative("case-2", List.of("step"), "Case 2", List.of(), 0.9, Instant.now());
        var level = new EventLevel("decision-narratives", 2);
        narrativeBus.publish(new LevelEvent<>(n1, Instant.now().toEpochMilli(), level, null));
        narrativeBus.publish(new LevelEvent<>(n2, Instant.now().toEpochMilli(), level, null));

        assertThat(resource.get("case-1")).hasSize(1);
        assertThat(resource.get("case-2")).hasSize(1);
    }
}
