package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalPlanAdapterTest {

    private final ClinicalPlanAdapter adapter = new ClinicalPlanAdapter();

    private ScoredCbrCase<PlanCbrCase> buildCase(Map<String, FeatureValue> features,
                                                  List<PlanTrace> traces) {
        var cbrCase = new PlanCbrCase("problem", "solution", "COMPLETED", 1.0, features, traces, null, null);
        return new ScoredCbrCase<>(cbrCase, "case-1", 0.87);
    }

    @Test
    void rule1_failedStep_isSuppressed() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "FAILED", 0, Map.of(), null);
        var scored = buildCase(Map.of("grade", FeatureValue.number(3)), List.of(trace));
        var current = Map.of("grade", (FeatureValue) FeatureValue.number(3));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, current);
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).action()).isEqualTo(AdaptationAction.SUPPRESSED);
        assertThat(result.steps().get(0).priority()).isZero();
        assertThat(result.steps().get(0).reason()).contains("failed");
    }

    @Test
    void rule1_terminatedStep_isSuppressed() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "TERMINATED", 0, Map.of(), null);
        var scored = buildCase(Map.of("grade", FeatureValue.number(3)), List.of(trace));
        var current = Map.of("grade", (FeatureValue) FeatureValue.number(3));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, current);
        assertThat(result.steps().get(0).action()).isEqualTo(AdaptationAction.SUPPRESSED);
    }

    @Test
    void rule2_completedStep_isBoosted() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "COMPLETED", 0, Map.of(), null);
        var scored = buildCase(Map.of("grade", FeatureValue.number(3)), List.of(trace));
        var current = Map.of("grade", (FeatureValue) FeatureValue.number(3));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, current);
        assertThat(result.steps().get(0).action()).isEqualTo(AdaptationAction.BOOSTED);
        assertThat(result.steps().get(0).priority()).isEqualTo(10);
        assertThat(result.steps().get(0).reason()).contains("succeeded");
    }

    @Test
    void rule3_higherGrade_boostsSafetySteps() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "COMPLETED", 0, Map.of(), null);
        var pastFeatures = Map.of("grade", (FeatureValue) FeatureValue.number(2));
        var scored = buildCase(pastFeatures, List.of(trace));
        var current = Map.of("grade", (FeatureValue) FeatureValue.number(4));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, current);
        assertThat(result.steps().get(0).priority()).isEqualTo(15);
        assertThat(result.steps().get(0).reason()).contains("Higher severity");
    }

    @Test
    void rule3_doesNotBoostSuppressedSteps() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "FAILED", 0, Map.of(), null);
        var pastFeatures = Map.of("grade", (FeatureValue) FeatureValue.number(2));
        var scored = buildCase(pastFeatures, List.of(trace));
        var current = Map.of("grade", (FeatureValue) FeatureValue.number(4));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, current);
        assertThat(result.steps().get(0).action()).isEqualTo(AdaptationAction.SUPPRESSED);
        assertThat(result.steps().get(0).priority()).isZero();
    }

    @Test
    void rule3_sameGrade_noExtraBoost() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "COMPLETED", 0, Map.of(), null);
        var features = Map.of("grade", (FeatureValue) FeatureValue.number(3));
        var scored = buildCase(features, List.of(trace));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, features);
        assertThat(result.steps().get(0).priority()).isEqualTo(10);
    }

    @Test
    void rule4_susarCondition_addsStep() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "COMPLETED", 0, Map.of(), null);
        var pastFeatures = Map.<String, FeatureValue>of(
                "grade", FeatureValue.number(3),
                "unexpected", FeatureValue.string("false"),
                "suspected", FeatureValue.string("false"));
        var scored = buildCase(pastFeatures, List.of(trace));
        var current = Map.<String, FeatureValue>of(
                "grade", FeatureValue.number(3),
                "unexpected", FeatureValue.string("true"),
                "suspected", FeatureValue.string("true"));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, current);
        assertThat(result.steps()).hasSize(2);
        var susarStep = result.steps().stream()
                .filter(s -> s.action() == AdaptationAction.ADDED).findFirst().orElseThrow();
        assertThat(susarStep.bindingName()).isEqualTo("susar-oversight");
        assertThat(susarStep.capabilityName()).isEqualTo("susar-review");
        assertThat(susarStep.priority()).isEqualTo(20);
        assertThat(susarStep.workerName()).isNull();
        assertThat(susarStep.stepOutcome()).isNull();
    }

    @Test
    void rule4_pastAlsoSusar_noAddition() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "COMPLETED", 0, Map.of(), null);
        var features = Map.<String, FeatureValue>of(
                "grade", FeatureValue.number(3),
                "unexpected", FeatureValue.string("true"),
                "suspected", FeatureValue.string("true"));
        var scored = buildCase(features, List.of(trace));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, features);
        assertThat(result.steps()).hasSize(1);
    }

    @Test
    void nonAeCaseType_passesThrough() {
        var trace = new PlanTrace("some-binding", "some-cap", "worker", "COMPLETED", 0, Map.of(), null);
        var scored = buildCase(Map.of(), List.of(trace));

        AdaptedPlan result = adapter.adapt("other-type", scored, Map.of());
        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().get(0).action()).isEqualTo(AdaptationAction.RETAINED);
        assertThat(result.steps().get(0).priority()).isZero();
    }

    @Test
    void missingGradeFeature_skipsRule3() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "COMPLETED", 0, Map.of(), null);
        var scored = buildCase(Map.of(), List.of(trace));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, Map.of());
        assertThat(result.steps().get(0).priority()).isEqualTo(10);
    }

    @Test
    void combinedRules_reasonConcatenation() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "COMPLETED", 0, Map.of(), null);
        var pastFeatures = Map.of("grade", (FeatureValue) FeatureValue.number(2));
        var scored = buildCase(pastFeatures, List.of(trace));
        var current = Map.of("grade", (FeatureValue) FeatureValue.number(4));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, current);
        String reason = result.steps().get(0).reason();
        assertThat(reason).contains("succeeded");
        assertThat(reason).contains("Higher severity");
    }

    @Test
    void retainedStep_unknownOutcome() {
        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "RUNNING", 0, Map.of(), null);
        var scored = buildCase(Map.of("grade", FeatureValue.number(3)), List.of(trace));
        var current = Map.of("grade", (FeatureValue) FeatureValue.number(3));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, current);
        assertThat(result.steps().get(0).action()).isEqualTo(AdaptationAction.RETAINED);
        assertThat(result.steps().get(0).priority()).isZero();
    }
}
