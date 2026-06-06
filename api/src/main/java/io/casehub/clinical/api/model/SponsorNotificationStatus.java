package io.casehub.clinical.api.model;

public enum SponsorNotificationStatus {
    /** Created by notify(); not yet attempted. */
    PENDING,
    /** Last attempt failed; retries remain. nextRetryAfter governs next pickup. */
    FAILED,
    /** Connector confirmed delivery. Terminal success. */
    DELIVERED,
    /** All attempts consumed; sponsor unreached. Terminal failure. */
    EXHAUSTED
}
