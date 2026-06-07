package io.casehub.clinical.service;

import io.casehub.clinical.api.SponsorNotificationExhaustedEvent;
import io.casehub.clinical.api.SponsorNotificationRetryPolicy;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.entity.SponsorNotification;
import io.casehub.clinical.api.model.SponsorNotificationStatus;
import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import io.quarkus.arc.All;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Handles a single sponsor notification delivery attempt (all three phases).
 *
 * <p>NOT {@code @Transactional} at the outer method — the connector call must be outside any
 * transaction to avoid holding Agroal connections during HTTP calls.
 *
 * <p>Phase 1: {@code store.load(id)} — short REQUIRED read tx; entity detaches on return.
 * Phase 2: {@code connector.send()} — no transaction.
 * Phase 3: {@code store.markDelivered/Failed/Exhausted()} — REQUIRES_NEW, atomic with ledger write.
 *
 * <p>Package-private — accessed only by {@link SponsorNotificationRetryJob} (same package).
 */
@ApplicationScoped
class SponsorNotificationDeliveryService {

    private static final Logger LOG = Logger.getLogger(SponsorNotificationDeliveryService.class);

    private final Map<String, Connector> connectorRegistry;

    @Inject SponsorNotificationStore store;
    @Inject DeviationLedgerWriter deviationLedgerWriter;
    @Inject Event<SponsorNotificationExhaustedEvent> exhaustedEvents;
    @Inject PreferenceProvider preferenceProvider;
    @Inject Clock clock;

    @Inject
    SponsorNotificationDeliveryService(@All final List<Connector> connectors) {
        this.connectorRegistry = connectors.stream()
                .collect(Collectors.toMap(Connector::id, Function.identity()));
    }

    /**
     * Attempts delivery of the notification identified by {@code notificationId}.
     *
     * <p>Idempotent if the notification is already in a terminal state (DELIVERED or EXHAUSTED);
     * Phase 1 status check returns early. This guards against the rare case of concurrent pickup
     * in future multi-node deployments (single-node assumed; see deployment constraints in spec).
     */
    void attemptDelivery(final UUID notificationId) {
        // Phase 1 — load entity in short read transaction
        final SponsorNotification n = store.load(notificationId);
        if (n == null) {
            LOG.warnf("SponsorNotificationDeliveryService: notificationId=%s not found — skipping", notificationId);
            return;
        }
        if (n.status == SponsorNotificationStatus.DELIVERED
                || n.status == SponsorNotificationStatus.EXHAUSTED) {
            LOG.debugf("SponsorNotificationDeliveryService: notificationId=%s already terminal (%s) — skipping",
                    notificationId, n.status);
            return;
        }

        final int attemptNumber = n.attempts + 1;

        final SponsorNotificationRetryPolicy policy = preferenceProvider
                .resolve(SettingsScope.of("casehubio", "clinical"))
                .getOrDefault(SponsorNotificationRetryPolicy.KEY);
        final int maxAttempts = policy.maxAttempts();

        // Phase 2 — connector call, no transaction
        final Connector connector = connectorRegistry.get(n.connectorId);
        if (connector == null) {
            final String reason = "connector-not-found: " + n.connectorId;
            LOG.errorf("SponsorNotificationDeliveryService: notificationId=%s connectorId=%s not found",
                    notificationId, n.connectorId);
            phase3Failure(notificationId, n, reason, attemptNumber, maxAttempts, policy);
            return;
        }

        final Instant deliveredAt;
        try {
            connector.send(new ConnectorMessage(n.destination, buildTitle(n), buildBody(n)));
            deliveredAt = clock.instant();
        } catch (final Exception e) {
            LOG.errorf(e, "SponsorNotificationDeliveryService: delivery failed for notificationId=%s attempt=%d",
                    notificationId, attemptNumber);
            phase3Failure(notificationId, n, e.getMessage(), attemptNumber, maxAttempts, policy);
            return;
        }

        // Phase 3 — success: store.markDelivered() commits entity+notification ledger atomically (REQUIRES_NEW).
        // deviationLedgerWriter runs OUTSIDE the try block so its failure does not regress a committed DELIVERED
        // state back to FAILED (C1 fix).
        store.markDelivered(notificationId, n, attemptNumber, deliveredAt);
        try {
            deviationLedgerWriter.writeSponsorNotifiedEntry(
                    n.deviationId, n.siteId, n.severity, deliveredAt, n.piId, n.piDisplayName);
        } catch (final Exception e) {
            LOG.errorf(e, "SponsorNotificationDeliveryService: deviation ledger write failed after successful " +
                    "delivery for notificationId=%s — notification is DELIVERED, deviation chain incomplete",
                    notificationId);
        }
    }

    private void phase3Failure(final UUID id, final SponsorNotification snapshot,
                               final String reason, final int attemptNumber,
                               final int maxAttempts, final SponsorNotificationRetryPolicy policy) {
        if (attemptNumber >= maxAttempts) {
            final Instant now = clock.instant();
            // store.markExhausted commits entity+notification ledger atomically (REQUIRES_NEW).
            // deviation ledger and event are separate operations; their failure must not prevent
            // the entity from reaching EXHAUSTED (which prevents re-delivery).
            store.markExhausted(id, snapshot, reason, attemptNumber);
            try {
                deviationLedgerWriter.writeExhaustedNotificationEntry(
                        snapshot.deviationId, snapshot.siteId, snapshot.severity, now);
            } catch (final Exception e) {
                LOG.errorf(e, "SponsorNotificationDeliveryService: deviation ledger write failed on exhaustion " +
                        "for notificationId=%s — entity is EXHAUSTED, deviation chain incomplete", id);
            }
            exhaustedEvents.fireAsync(new SponsorNotificationExhaustedEvent(
                    id, snapshot.deviationId, snapshot.trialId, snapshot.siteId,
                    snapshot.severity, snapshot.terminalStatus, reason, attemptNumber));
        } else {
            final Instant nextRetry = clock.instant().plus(computeDelay(policy, attemptNumber));
            store.markFailed(id, snapshot, reason, attemptNumber, nextRetry);
        }
    }

    private static Duration computeDelay(final SponsorNotificationRetryPolicy policy,
                                        final int attemptNumber) {
        final Duration delay;
        if (policy.backoffMultiplier() == 1.0) {
            delay = policy.retryInterval();
        } else {
            final long baseMinutes = policy.retryInterval().toMinutes();
            final long delayMinutes =
                    Math.round(baseMinutes * Math.pow(policy.backoffMultiplier(), attemptNumber - 1));
            delay = Duration.ofMinutes(delayMinutes);
        }
        if (policy.maxInterval() != null && delay.compareTo(policy.maxInterval()) > 0) {
            return policy.maxInterval();
        }
        return delay;
    }

    private String buildTitle(final SponsorNotification n) {
        return "[" + n.severity.name() + " Deviation] " + n.deviationType
                + " — " + n.terminalStatus.name();
    }

    String buildBody(final SponsorNotification n) {
        return switch (n.terminalStatus) {
            case ESCALATED -> "PI " + n.piDisplayName + " approved — corrective action committed. "
                    + "Site: " + n.siteId + ". Type: " + n.deviationType + ". "
                    + "Ref: clinical/deviation/" + n.deviationId + "/pi-oversight";
            case REJECTED -> "PI " + n.piDisplayName + " refused to authorise — no corrective action. "
                    + "Site: " + n.siteId + ". Type: " + n.deviationType + ".";
            case EXPIRED -> "PI response deadline expired — no response received. "
                    + "Site: " + n.siteId + ". Type: " + n.deviationType + ".";
            default -> throw new IllegalArgumentException(
                    "Unexpected terminal status for sponsor notification: " + n.terminalStatus);
        };
    }
}
