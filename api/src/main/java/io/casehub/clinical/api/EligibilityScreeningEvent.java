package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.model.EligibilityScreeningResult;
import java.util.List;
import java.util.UUID;

public record EligibilityScreeningEvent(
    UUID enrollmentId,
    String tenantId,
    EligibilityScreeningResult screeningResult,
    List<CriterionResult> criteriaResults
) {}
