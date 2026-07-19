package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.AdaptedStep;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record EscalationPlanRecommendation(
        AdaptedPlan adaptedPlan,
        int retrievedCaseCount,
        double topSimilarityScore,
        String traceId,
        String explanation
) {
    public static EscalationPlanRecommendation none() {
        return new EscalationPlanRecommendation(null, 0, 0.0, null, null);
    }

    public boolean hasRecommendation() {
        return adaptedPlan != null;
    }

    public Map<String, Object> toContextMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("retrievedCaseCount", retrievedCaseCount);
        map.put("topSimilarityScore", topSimilarityScore);
        map.put("traceId", traceId);
        map.put("explanation", explanation);
        List<Map<String, Object>> stepMaps = adaptedPlan != null
                ? adaptedPlan.steps().stream().map(this::stepToMap).toList()
                : List.of();
        map.put("steps", stepMaps);
        return map;
    }

    private Map<String, Object> stepToMap(AdaptedStep step) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bindingName", step.bindingName());
        m.put("capabilityName", step.capabilityName());
        m.put("workerName", step.workerName());
        m.put("stepOutcome", step.stepOutcome());
        m.put("action", step.action().name());
        m.put("priority", step.priority());
        m.put("reason", step.reason());
        return m;
    }
}
