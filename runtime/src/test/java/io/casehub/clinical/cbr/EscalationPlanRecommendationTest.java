package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.AdaptationAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EscalationPlanRecommendationTest {

    @Test
    void none_returnsEmptyRecommendation() {
        var rec = EscalationPlanRecommendation.none();
        assertThat(rec.hasRecommendation()).isFalse();
        assertThat(rec.retrievedCaseCount()).isZero();
        assertThat(rec.adaptedPlan()).isNull();
    }

    @Test
    void hasRecommendation_trueWhenPlanPresent() {
        var step = new AdaptedStep("safety-review", "safety-monitoring", "worker-1",
                "COMPLETED", 10, Map.of(), AdaptationAction.BOOSTED,
                "Step succeeded in past similar case (similarity: 0.87).");
        var plan = new AdaptedPlan(List.of(step));
        var rec = new EscalationPlanRecommendation(plan, 3, 0.87, "trace-1", "explanation");
        assertThat(rec.hasRecommendation()).isTrue();
    }

    @Test
    void toContextMap_containsAllFields() {
        var step = new AdaptedStep("safety-review", "safety-monitoring", "worker-1",
                "COMPLETED", 10, Map.of(), AdaptationAction.BOOSTED, "reason");
        var plan = new AdaptedPlan(List.of(step));
        var rec = new EscalationPlanRecommendation(plan, 2, 0.85, "trace-id", "expl");

        Map<String, Object> ctx = rec.toContextMap();
        assertThat(ctx).containsEntry("retrievedCaseCount", 2);
        assertThat(ctx).containsEntry("topSimilarityScore", 0.85);
        assertThat(ctx).containsEntry("traceId", "trace-id");
        assertThat(ctx).containsEntry("explanation", "expl");
        assertThat(ctx).containsKey("steps");
        @SuppressWarnings("unchecked")
        var steps = (List<Map<String, Object>>) ctx.get("steps");
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0)).containsEntry("bindingName", "safety-review");
        assertThat(steps.get(0)).containsEntry("action", "BOOSTED");
    }

    @Test
    void toContextMap_noneReturnsEmptySteps() {
        var rec = EscalationPlanRecommendation.none();
        Map<String, Object> ctx = rec.toContextMap();
        assertThat(ctx).containsEntry("retrievedCaseCount", 0);
        @SuppressWarnings("unchecked")
        var steps = (List<Map<String, Object>>) ctx.get("steps");
        assertThat(steps).isEmpty();
    }
}
