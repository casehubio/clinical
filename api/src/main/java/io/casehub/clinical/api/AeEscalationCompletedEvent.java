package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;
import java.time.Instant;
import java.util.UUID;

/**
 * CDI event fired when an AE escalation case completes — all required safety
 * reviews (senior monitor, and DSMB if Grade 4+) have been resolved.
 *
 * <p>{@code unexpected} is derived from the case context at fire time (set by
 * AeEscalationCaseService from AdverseEvent.unexpected, added in Layer 8).
 * It is a material fact about the AE that belongs in the completion event for
 * downstream consumers.
 */
public record AeEscalationCompletedEvent(
    UUID aeId,
    CtcaeGrade grade,
    UUID siteId,
    String safetyReviewOutcome,
    boolean dsmbEscalated,
    Instant completedAt,
    boolean unexpected) {}
