package io.casehub.clinical.api.model;

public record PlanStepResponse(
    String bindingName,
    String capabilityName,
    String workerName,
    String stepOutcome
) {}
