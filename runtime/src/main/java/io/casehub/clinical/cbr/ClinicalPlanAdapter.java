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
    public AdaptedPlan adapt(String caseType, ScoredCbrCase<ResolvedCase> retrieved,
                             Map<String, FeatureValue> currentFeatures) {
        if (!CASE_TYPE_AE.equals(caseType)) {
            return passThrough(retrieved);
        }

        List<AdaptedStep> steps = new ArrayList<>();

        if (shouldAddSusar(currentFeatures, retrieved.cbrCase().features())) {
            steps.add(new AdaptedStep("susar-oversight", "susar-review", null, null,
                    20, Map.of(), AdaptationAction.ADDED,
                    "Current AE meets SUSAR criteria — not present in precedent case."));
        }

        return new AdaptedPlan(steps);
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

    private AdaptedPlan passThrough(ScoredCbrCase<ResolvedCase> retrieved) {
        return new AdaptedPlan(List.of());
    }
}
