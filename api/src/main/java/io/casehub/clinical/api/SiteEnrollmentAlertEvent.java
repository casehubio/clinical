package io.casehub.clinical.api;

import java.util.UUID;

public record SiteEnrollmentAlertEvent(
    UUID siteId, UUID trialId,
    int matchCount, double topScore,
    String predictedOutcome, double predictedProbability,
    String traceId, String tenantId) {}
