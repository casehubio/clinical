package io.casehub.clinical.api.spi;

/**
 * Resolves a PI actor identifier to the formal name used in GCP-regulated sponsor notifications.
 *
 * <p>Deployers override the {@code @DefaultBean} to integrate with an identity store,
 * Qhorus actor registry, LDAP directory, or configuration map.
 *
 * <p><strong>Contract (caller guarantees):</strong>
 * <ul>
 *   <li>{@code actorId} is never null — the caller (SponsorNotificationListener) guards
 *       before invoking. Implementations need not handle null.</li>
 * </ul>
 *
 * <p><strong>Contract (implementation obligations):</strong>
 * <ul>
 *   <li>Actor not found: return {@code actorId} unchanged. Do not throw.</li>
 *   <li>Transient failure (timeout, service unavailable): return {@code actorId} unchanged
 *       and log the failure internally. Do not throw — throwing causes the event thread to
 *       write a {@code sponsor-notifier-pi-resolver-failed} audit entry and halt notification.</li>
 *   <li>Must be thread-safe. Caching (e.g. {@code ConcurrentHashMap}) is encouraged —
 *       PI identities change rarely. Cache aggressively.</li>
 *   <li>Advisory latency: keep well below the notification delivery path's total budget.
 *       The caller does not enforce a timeout. Deployers are responsible for their
 *       implementation's latency.</li>
 * </ul>
 *
 * <p><strong>actorId formats:</strong> any string from
 * {@code SponsorNotificationRequest.piId()} — may be a Qhorus actor handle
 * (e.g. {@code claude:pi@v1}), a human identity reference, or an opaque system ID.
 *
 * <p><strong>Scope:</strong> PI-specific. No other current notifier requires actor name
 * resolution. Generalise to a shared {@code ActorIdentityResolver} if a future notifier
 * requires the same capability.
 */
public interface PiIdentityResolver {
    String resolveFormalName(String actorId);
}
