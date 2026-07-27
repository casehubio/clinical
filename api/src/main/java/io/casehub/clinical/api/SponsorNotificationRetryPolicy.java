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
 * <p>{@code retryInterval} is the base wait between attempts.
 *
 * <p>{@code backoffMultiplier} (≥1.0, default 1.0) scales the delay exponentially:
 * {@code delay = retryInterval × backoffMultiplier^(attemptNumber − 1)}.
 * With 1.0 the interval is fixed; with 2.0 it doubles each attempt.
 *
 * <p>{@code maxInterval} caps the computed delay (null = no cap).
 *
 * <p>Configured via MicroProfile Config:
 * <pre>
 *   casehub.platform.preferences.defaults.casehubio.clinical.sponsorNotifierRetryPolicy=3,30
 *   casehub.platform.preferences.defaults.casehubio.clinical.sponsorNotifierRetryPolicy=3,15,2.0
 *   casehub.platform.preferences.defaults.casehubio.clinical.sponsorNotifierRetryPolicy=3,15,2.0,120
 * </pre>
 * Format: {@code "<maxAttempts>,<retryIntervalMinutes>[,<backoffMultiplier>[,<maxIntervalMinutes>]]"}
 */
public record SponsorNotificationRetryPolicy(
        int maxAttempts,
        Duration retryInterval,
        double backoffMultiplier,
        Duration maxInterval)
        implements SingleValuePreference {

    public static final SponsorNotificationRetryPolicy DEFAULT =
            new SponsorNotificationRetryPolicy(3, Duration.ofMinutes(30), 1.0, null);

    public static final PreferenceKey<SponsorNotificationRetryPolicy> KEY =
            new PreferenceKey<>(
                    "casehubio.clinical",
                    "sponsorNotifierRetryPolicy",
                    DEFAULT,
                    s -> {
                        final String[] parts = s.split(",");
                        if (parts.length < 2 || parts.length > 4) {
                            throw new IllegalArgumentException(
                                    "sponsorNotifierRetryPolicy must be"
                                    + " \"maxAttempts,retryIntervalMinutes[,backoffMultiplier[,maxIntervalMinutes]]\","
                                    + " got: " + s);
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
                        double multiplier = 1.0;
                        Duration maxInterval = null;
                        if (parts.length >= 3) {
                            multiplier = Double.parseDouble(parts[2].trim());
                            if (multiplier < 1.0) {
                                throw new IllegalArgumentException(
                                        "backoffMultiplier must be >= 1.0, got: " + multiplier);
                            }
                        }
                        if (parts.length == 4) {
                            final int maxMinutes = Integer.parseInt(parts[3].trim());
                            if (maxMinutes < 1) {
                                throw new IllegalArgumentException(
                                        "maxIntervalMinutes must be >= 1, got: " + maxMinutes);
                            }
                            maxInterval = Duration.ofMinutes(maxMinutes);
                        }
                        return new SponsorNotificationRetryPolicy(
                                maxAttempts, Duration.ofMinutes(intervalMinutes), multiplier, maxInterval);
                    });

    @Override
    public String toSerializedValue() {
        final StringBuilder sb = new StringBuilder();
        sb.append(maxAttempts).append(',').append(retryInterval.toMinutes());
        if (backoffMultiplier != 1.0 || maxInterval != null) {
            sb.append(',').append(backoffMultiplier);
        }
        if (maxInterval != null) {
            sb.append(',').append(maxInterval.toMinutes());
        }
        return sb.toString();
    }
}
