package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;

import java.util.UUID;

public record AeEscalationFailedEvent(
    UUID aeId,
    CtcaeGrade grade,
    String tenantId,
    String errorMessage) {}
