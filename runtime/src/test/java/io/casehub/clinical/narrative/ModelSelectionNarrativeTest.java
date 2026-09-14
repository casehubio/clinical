package io.casehub.clinical.narrative;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;
import io.casehub.blocks.summarisation.narrative.StepOutcome;
import io.casehub.clinical.agent.ModelSelectionEvent;
import io.casehub.platform.api.model.ModelTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ModelSelectionNarrativeTest {

    private List<DecisionSignal> captured;
    private ClinicalNarrativeSignalStrategy strategy;

    @BeforeEach
    void setUp() {
        EventStreamBus<DecisionSignal> signalBus = new EventStreamBus<>();
        captured = new ArrayList<>();
        signalBus.subscribe(e -> true, (LevelEvent<DecisionSignal> event) -> captured.add(event.payload()));
        var pipeline = mock(DecisionNarrativePipeline.class);
        strategy = new ClinicalNarrativeSignalStrategy(signalBus, pipeline);
    }

    @Test
    void emitsStepOutcomeForModelSelection() {
        var event = new ModelSelectionEvent(
                "susar-criteria", ModelTier.FLAGSHIP, "claude-opus-4");

        strategy.onModelSelection(event);

        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst()).isInstanceOf(StepOutcome.class);
        var outcome = (StepOutcome) captured.getFirst();
        assertThat(outcome.status()).isEqualTo("MODEL_SELECTED");
        assertThat(outcome.workerId()).isEqualTo("claude-opus-4");
        assertThat(outcome.stepName()).isEqualTo("susar-criteria");
        assertThat(outcome.elapsed()).isEqualTo(Duration.ZERO);
    }

    @Test
    void emitsForStandardTier() {
        var event = new ModelSelectionEvent(
                "eligibility-screening", ModelTier.STANDARD, "claude-sonnet-5");

        strategy.onModelSelection(event);

        assertThat(captured).hasSize(1);
        var outcome = (StepOutcome) captured.getFirst();
        assertThat(outcome.workerId()).isEqualTo("claude-sonnet-5");
        assertThat(outcome.stepName()).isEqualTo("eligibility-screening");
    }

    @Test
    void usesTierNameAsCaseId() {
        var event = new ModelSelectionEvent(
                "safety-monitoring", ModelTier.FLAGSHIP, "claude-opus-4");

        strategy.onModelSelection(event);

        var outcome = (StepOutcome) captured.getFirst();
        assertThat(outcome.caseId()).isEqualTo("model-selection:FLAGSHIP");
    }
}
