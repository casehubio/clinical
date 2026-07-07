package io.casehub.clinical.api.model;

import java.util.List;

public record DeviationPrecedentResponse(
    double score,
    String deviationType,
    String severity,
    String escalationRequirement,
    String piDecision,
    String irbDecision,
    List<PlanStepResponse> steps,
    String problem,
    String outcome
) {}
