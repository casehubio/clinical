package io.casehub.clinical.narrative;

import io.casehub.api.spi.StepOutcomeEvent;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.narrative.CbrRetrieval;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;
import io.casehub.blocks.summarisation.narrative.RoutingDecision;
import io.casehub.blocks.summarisation.narrative.StepOutcome;
import io.casehub.blocks.summarisation.narrative.TrustAssessment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClinicalNarrativeSignalStrategyTest {

    private EventStreamBus<DecisionSignal> signalBus;
    private List<DecisionSignal> captured;
    private ClinicalNarrativeSignalStrategy strategy;

    @BeforeEach
    void setUp() {
        signalBus = new EventStreamBus<>();
        captured = new ArrayList<>();
        signalBus.subscribe(e -> true, (LevelEvent<DecisionSignal> event) -> captured.add(event.payload()));
        var pipeline = mock(DecisionNarrativePipeline.class);
        strategy = new ClinicalNarrativeSignalStrategy(signalBus, pipeline);
    }

    @Test
    void emitsStepOutcomeForSusarOversight() {
        strategy.onStepOutcome(stepEvent("susar-oversight", Map.of()));

        assertThat(captured).anyMatch(s -> s instanceof StepOutcome);
    }

    @Test
    void emitsStepOutcomeForAeEscalation() {
        strategy.onStepOutcome(stepEvent("ae-escalation", Map.of()));

        assertThat(captured).anyMatch(s -> s instanceof StepOutcome);
    }

    @Test
    void emitsForAllSevenCaseTypes() {
        var types = List.of("trial-coordination", "susar-oversight", "ae-escalation",
                "eligibility-screening", "protocol-amendment",
                "deviation-review", "regulatory-submission");

        for (String type : types) {
            captured.clear();
            strategy.onStepOutcome(stepEvent(type, Map.of()));
            assertThat(captured).as("Should emit for case type: " + type)
                    .anyMatch(s -> s instanceof StepOutcome);
        }
    }

    @Test
    void skipsNonClinicalCaseType() {
        strategy.onStepOutcome(stepEvent("overnight-incident", Map.of()));

        assertThat(captured).isEmpty();
    }

    @Test
    void emitsRoutingDecisionWhenRoutedAgentPresent() {
        var event = new StepOutcomeEvent(UUID.randomUUID(), "default",
                "susar-oversight", "susar-assessment", "safety-monitoring",
                "susar-criteria-evaluator", RoutingOutcome.SUCCESS,
                Map.of("routedAgentId", "agent-1", "routingScore", "0.85"),
                Duration.ofSeconds(3));

        strategy.onStepOutcome(event);

        assertThat(captured).anyMatch(s -> s instanceof RoutingDecision rd
                && rd.selectedAgentId().equals("agent-1"));
    }

    @Test
    void emitsTrustAssessmentWhenTrustScorePresent() {
        var event = new StepOutcomeEvent(UUID.randomUUID(), "default",
                "susar-oversight", "susar-assessment", "safety-monitoring",
                "evaluator", RoutingOutcome.SUCCESS,
                Map.of("trustScore", "0.92"),
                Duration.ofSeconds(2));

        strategy.onStepOutcome(event);

        assertThat(captured).anyMatch(s -> s instanceof TrustAssessment ta
                && ta.agentId().equals("evaluator")
                && ta.trustScore() == 0.92);
    }

    @Test
    void emitsCbrRetrievalWhenCbrDataPresent() {
        var event = new StepOutcomeEvent(UUID.randomUUID(), "default",
                "ae-escalation", "safety-review", null,
                "worker", RoutingOutcome.SUCCESS,
                Map.of("cbrRetrievedCount", "3", "cbrTopSimilarity", "0.78"),
                Duration.ofSeconds(1));

        strategy.onStepOutcome(event);

        assertThat(captured).anyMatch(s -> s instanceof CbrRetrieval cbr
                && cbr.retrievedCount() == 3
                && cbr.topSimilarity() == 0.78);
    }

    @Test
    void handlesNullExecutionDuration() {
        var event = new StepOutcomeEvent(UUID.randomUUID(), "default",
                "susar-oversight", "step", null,
                "worker", RoutingOutcome.SUCCESS, Map.of(), null);

        strategy.onStepOutcome(event);

        assertThat(captured).anyMatch(s -> s instanceof StepOutcome so
                && so.elapsed().equals(Duration.ZERO));
    }

    @Test
    void convertsUuidCaseIdToString() {
        UUID caseId = UUID.randomUUID();
        var event = new StepOutcomeEvent(caseId, "default",
                "susar-oversight", "step", null,
                "worker", RoutingOutcome.SUCCESS, Map.of(), Duration.ofSeconds(1));

        strategy.onStepOutcome(event);

        assertThat(captured).anyMatch(s -> s.caseId().equals(caseId.toString()));
    }

    private StepOutcomeEvent stepEvent(String caseType, Map<String, Object> context) {
        return new StepOutcomeEvent(UUID.randomUUID(), "default",
                caseType, "test-step", null, "test-worker",
                RoutingOutcome.SUCCESS, context, Duration.ofSeconds(1));
    }
}
