package io.casehub.clinical.api.view;

public record EscalationStepView(
        String bindingName,
        String capabilityName,
        String workerName,
        String stepOutcome,
        String action,
        int priority,
        String reason
) {}
