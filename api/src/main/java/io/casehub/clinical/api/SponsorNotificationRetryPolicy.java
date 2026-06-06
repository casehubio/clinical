package io.casehub.clinical.api;

import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.SingleValuePreference;
import java.time.Duration;

/**
 * Retry policy for sponsor notification delivery.
 *
 * <p>{@code maxAttempts} is the total number of delivery attempts, including
 * the first. Minimum 1 (one attempt, no retry). With maxAttempts=1 and a
 * failure, the notification goes straight to EXHAUSTED — equivalent to
 * fire-and-forget but with a full audit trail.
 *
 * <p>{@code retryInterval} is the fixed wait between attempts. The scheduler
 * polls independently at its own interval; {@code retryInterval} governs the
 * per-notification nextRetryAfter delay.
 *
 * <p>Configured via MicroProfile Config:
 * <pre>
 *   casehub.platform.preferences.defaults.casehubio.clinical.sponsorNotifierRetryPolicy=3,30
 * </pre>
 * Format: {@code "<maxAttempts>,<retryIntervalMinutes>"} — e.g. {@code "3,30"}
 * means 3 total attempts, 30-minute interval.
 */
public record SponsorNotificationRetryPolicy(int maxAttempts, Duration retryInterval)
        implements SingleValuePreference {

    public static final SponsorNotificationRetryPolicy DEFAULT =
            new SponsorNotificationRetryPolicy(3, Duration.ofMinutes(30));

    public static final PreferenceKey<SponsorNotificationRetryPolicy> KEY =
            new PreferenceKey<>(
                    "casehubio.clinical",
                    "sponsorNotifierRetryPolicy",
                    DEFAULT,
                    s -> {
                        final String[] parts = s.split(",");
                        if (parts.length != 2) {
                            throw new IllegalArgumentException(
                                    "sponsorNotifierRetryPolicy must be \"maxAttempts,retryIntervalMinutes\", got: " + s);
                        }
                        final int maxAttempts = Integer.parseInt(parts[0].trim());
                        final int intervalMinutes = Integer.parseInt(parts[1].trim());
                        if (maxAttempts < 1) {
                            throw new IllegalArgumentException(
                                    "maxAttempts must be >= 1, got: " + maxAttempts);
                        }
                        if (intervalMinutes < 1) {
                            throw new IllegalArgumentException(
                                    "retryIntervalMinutes must be >= 1, got: " + intervalMinutes);
                        }
                        return new SponsorNotificationRetryPolicy(maxAttempts, Duration.ofMinutes(intervalMinutes));
                    });
}
