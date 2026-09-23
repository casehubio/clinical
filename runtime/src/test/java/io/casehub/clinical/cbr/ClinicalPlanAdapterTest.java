package io.casehub.clinical.cbr;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClinicalPlanAdapterTest {

    private final ClinicalPlanAdapter adapter = new ClinicalPlanAdapter();

    private CbrMatch<CbrPlanRecord> buildCase(Map<String, FeatureValue> features) {
        var cbrCase = new CbrPlanRecord("problem", "solution", "COMPLETED", Confidence.unknown(1.0), features, List.of(), null, null);
        return new CbrMatch<>(cbrCase, "case-1", "clinical-ae", 0.87);
    }

    @Test
    void rule4_susarCondition_addsStep() {
        var pastFeatures = Map.<String, FeatureValue>of(
                "grade", FeatureValue.number(3),
                "unexpected", FeatureValue.string("false"),
                "suspected", FeatureValue.string("false"));
        var scored = buildCase(pastFeatures);
        var current = Map.<String, FeatureValue>of(
                "grade", FeatureValue.number(3),
                "unexpected", FeatureValue.string("true"),
                "suspected", FeatureValue.string("true"));

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, current);
        assertThat(result.steps()).hasSize(1);
        var susarStep = result.steps().get(0);
        assertThat(susarStep.action()).isEqualTo(AdaptationAction.ADDED);
        assertThat(susarStep.bindingName()).isEqualTo("susar-oversight");
        assertThat(susarStep.capabilityName()).isEqualTo("susar-review");
        assertThat(susarStep.priority()).isEqualTo(20);
    }

    @Test
    void rule4_pastAlsoSusar_noAddition() {
        var features = Map.<String, FeatureValue>of(
                "grade", FeatureValue.number(3),
                "unexpected", FeatureValue.string("true"),
                "suspected", FeatureValue.string("true"));
        var scored = buildCase(features);

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, features);
        assertThat(result.steps()).isEmpty();
    }

    @Test
    void nonAeCaseType_returnsEmptyPlan() {
        var scored = buildCase(Map.of());

        AdaptedPlan result = adapter.adapt("other-type", scored, Map.of());
        assertThat(result.steps()).isEmpty();
    }

    @Test
    void aeCase_noSusarCondition_returnsEmptyPlan() {
        var features = Map.<String, FeatureValue>of("grade", FeatureValue.number(3));
        var scored = buildCase(features);

        AdaptedPlan result = adapter.adapt("clinical-ae", scored, features);
        assertThat(result.steps()).isEmpty();
    }
}
