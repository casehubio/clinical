package io.casehub.clinical.agent;

import java.util.List;

public record EligibilityResponse(List<CriterionEvaluation> criteria) {
    public record CriterionEvaluation(String criterionId, boolean met, boolean marginal, String reasoning) {}
}
