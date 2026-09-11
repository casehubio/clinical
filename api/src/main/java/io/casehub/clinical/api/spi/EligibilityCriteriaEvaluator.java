package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.model.CriterionResult;

import java.util.List;
import java.util.UUID;

public interface EligibilityCriteriaEvaluator {
    List<CriterionResult> evaluate(UUID enrollmentId, String tenantId, List<String> protocolCriteria);
}
