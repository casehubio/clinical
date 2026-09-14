package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;

import java.util.UUID;

public record AeEscalationStartedEvent(
    UUID aeId,
    UUID caseId,
    CtcaeGrade grade,
    String tenantId) {}
