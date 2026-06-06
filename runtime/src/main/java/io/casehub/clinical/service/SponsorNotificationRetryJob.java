package io.casehub.clinical.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.time.Clock;
import java.util.UUID;

/**
 * Polls for eligible sponsor notifications (PENDING or FAILED with past nextRetryAfter) and
 * delegates to {@link SponsorNotificationDeliveryService} per notification.
 *
 * <p>NOT {@code @Transactional} — per GE-20260522-44bbf3, each delivery is isolated; one failure
 * must not roll back or abandon the remaining items. Each call to {@code attemptDelivery} is
 * wrapped in try-catch-log so a single notification throwing does not abort the loop.
 *
 * <p>Excluded from tests via {@code quarkus.arc.exclude-types}. Tests drive delivery directly
 * via {@link SponsorNotificationDeliveryService#attemptDelivery}.
 *
 * <p>Single-node only — no distributed locking. See deployment constraints in the spec.
 */
@ApplicationScoped
class SponsorNotificationRetryJob {

    private static final Logger LOG = Logger.getLogger(SponsorNotificationRetryJob.class);

    @Inject SponsorNotificationStore store;
    @Inject SponsorNotificationDeliveryService delivery;
    @Inject Clock clock;

    @ConfigProperty(name = "casehub.clinical.sponsor-notifier.batch-size", defaultValue = "100")
    int batchSize;

    @Scheduled(every = "${casehub.clinical.sponsor-notifier.poll-interval:60}s")
    void tick() {
        for (final UUID id : store.findEligibleIds(clock.instant(), batchSize)) {
            try {
                delivery.attemptDelivery(id);
            } catch (final Exception e) {
                LOG.errorf(e,
                        "SponsorNotificationRetryJob: unhandled error for notificationId=%s — skipping", id);
            }
        }
    }
}
