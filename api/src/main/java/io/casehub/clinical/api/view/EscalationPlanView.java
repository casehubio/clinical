package io.casehub.clinical.api.view;

import java.util.List;

public record EscalationPlanView(
        int retrievedCaseCount,
        double topSimilarityScore,
        String traceId,
        String explanation,
        List<EscalationStepView> steps
) {}
