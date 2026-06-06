package io.casehub.clinical.api;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import java.util.UUID;

/**
 * Fired when all retry attempts for a sponsor notification are consumed and
 * the sponsor remains unreached. Named "Exhausted" (not "Failed") because
 * FAILED is an intermediate status meaning retries remain.
 *
 * <p>No consumer is defined in this version — this is the extension point for
 * WorkItem escalation (casehubio/clinical#60).
 */
public record SponsorNotificationExhaustedEvent(
    UUID              notificationId,
    UUID              deviationId,
    UUID              trialId,
    UUID              siteId,
    DeviationSeverity severity,
    PiApprovalStatus  terminalStatus,
    String            failureReason,
    int               totalAttempts
) {}
