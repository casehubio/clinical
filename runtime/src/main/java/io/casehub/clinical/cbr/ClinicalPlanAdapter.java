package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ClinicalPlanAdapter implements PlanAdapter {

    private static final String CASE_TYPE_AE = "clinical-ae";
    private static final Set<String> SAFETY_CAPABILITIES = Set.of("safety-monitoring", "data-safety-monitoring");

    @Override
    public AdaptedPlan adapt(String caseType, ScoredCbrCase<PlanCbrCase> retrieved,
                             Map<String, FeatureValue> currentFeatures) {
        if (!CASE_TYPE_AE.equals(caseType)) {
            return passThrough(retrieved);
        }

        List<AdaptedStep> steps = new ArrayList<>();
        boolean gradeEscalated = isGradeEscalated(currentFeatures, retrieved.cbrCase().features());

        for (PlanTrace trace : retrieved.cbrCase().planTrace()) {
            steps.add(adaptStep(trace, retrieved.score(), gradeEscalated));
        }

        if (shouldAddSusar(currentFeatures, retrieved.cbrCase().features())) {
            steps.add(new AdaptedStep("susar-oversight", "susar-review", null, null,
                    20, Map.of(), AdaptationAction.ADDED,
                    "Current AE meets SUSAR criteria — not present in precedent case."));
        }

        return new AdaptedPlan(steps);
    }

    private AdaptedStep adaptStep(PlanTrace trace, double similarity, boolean gradeEscalated) {
        String outcome = trace.stepOutcome();

        if ("FAILED".equals(outcome) || "TERMINATED".equals(outcome)) {
            return new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                    trace.workerName(), trace.stepOutcome(), 0, trace.parameters(),
                    AdaptationAction.SUPPRESSED, "Step failed in past similar case.");
        }

        if ("COMPLETED".equals(outcome)) {
            int priority = 10;
            String reason = "Step succeeded in past similar case (similarity: %.2f).".formatted(similarity);

            if (gradeEscalated && SAFETY_CAPABILITIES.contains(trace.capabilityName())) {
                priority += 5;
                reason += " Higher severity than precedent — elevated urgency.";
            }

            return new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                    trace.workerName(), trace.stepOutcome(), priority, trace.parameters(),
                    AdaptationAction.BOOSTED, reason);
        }

        int priority = 0;
        String reason = null;
        if (gradeEscalated && SAFETY_CAPABILITIES.contains(trace.capabilityName())) {
            priority = 5;
            reason = "Higher severity than precedent — elevated urgency.";
        }

        return new AdaptedStep(trace.bindingName(), trace.capabilityName(),
                trace.workerName(), trace.stepOutcome(), priority, trace.parameters(),
                AdaptationAction.RETAINED, reason);
    }

    private boolean isGradeEscalated(Map<String, FeatureValue> current, Map<String, FeatureValue> past) {
        FeatureValue currentGrade = current.get("grade");
        FeatureValue pastGrade = past.get("grade");
        if (currentGrade instanceof FeatureValue.NumberVal c && pastGrade instanceof FeatureValue.NumberVal p) {
            return c.value() > p.value();
        }
        return false;
    }

    private boolean shouldAddSusar(Map<String, FeatureValue> current, Map<String, FeatureValue> past) {
        boolean currentSusar = isStringTrue(current, "unexpected") && isStringTrue(current, "suspected");
        boolean pastSusar = isStringTrue(past, "unexpected") && isStringTrue(past, "suspected");
        return currentSusar && !pastSusar;
    }

    private boolean isStringTrue(Map<String, FeatureValue> features, String key) {
        FeatureValue val = features.get(key);
        return val instanceof FeatureValue.StringVal s && "true".equals(s.value());
    }

    private AdaptedPlan passThrough(ScoredCbrCase<PlanCbrCase> retrieved) {
        List<AdaptedStep> steps = retrieved.cbrCase().planTrace().stream()
                .map(t -> new AdaptedStep(t.bindingName(), t.capabilityName(),
                        t.workerName(), t.stepOutcome(), 0, t.parameters(),
                        AdaptationAction.RETAINED, null))
                .toList();
        return new AdaptedPlan(steps);
    }
}
