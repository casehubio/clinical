package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;
import java.util.UUID;

public record AeTrajectoryAlertEvent(
    UUID aeId, UUID enrollmentId, UUID siteId,
    CtcaeGrade currentGrade,
    int matchCount, double topScore,
    String predictedOutcome, double predictedProbability,
    String traceId, String tenantId) {}
