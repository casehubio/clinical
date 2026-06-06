package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationRequest;
import io.casehub.clinical.api.SponsorNotifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Production-grade {@link SponsorNotifier} implementation.
 *
 * <p>Replaces {@code DefaultSponsorNotifier} (deleted). The delivery path is fully durable:
 * every attempt is persisted, retried on failure, and independently audited.
 *
 * <p>{@code notify()} persists a PENDING {@code SponsorNotification} entity and returns
 * immediately. Delivery is handled by {@link SponsorNotificationRetryJob}, which polls for
 * eligible notifications and calls {@link SponsorNotificationDeliveryService#attemptDelivery}.
 * This decouples the listener's transaction from the connector call.
 *
 * <p>With {@code maxAttempts = 1} (via {@code SponsorNotificationRetryPolicy}), the scheduler
 * makes one attempt and marks EXHAUSTED on failure — equivalent to fire-and-forget but with
 * a full audit trail.
 */
@ApplicationScoped
public class DurableSponsorNotifier implements SponsorNotifier {

    @Inject SponsorNotificationStore store;

    @Override
    public void notify(final SponsorNotificationRequest request) {
        store.createPending(request);
    }
}
