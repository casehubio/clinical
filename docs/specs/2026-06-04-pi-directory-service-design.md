# Design — PiIdentityResolver SPI

**Issue:** casehubio/clinical#23
**Date:** 2026-06-04

## Summary

Introduce `PiIdentityResolver` SPI to resolve PI actor IDs (e.g. `claude:pi@v1`) to formal,
human-readable names before building `ConnectorMessage` bodies in `DefaultSponsorNotifier`.
GCP-regulated sponsor notifications must identify PIs by legal name, not opaque system identifiers.

Resolution is performed in `SponsorNotificationListener` — not in `DefaultSponsorNotifier`.
This keeps the notifier as pure message formatting, separates resolution and delivery failures
in the audit trail, and makes the resolved name available for ledger recording.

**Scope:** PI-specific. No other current notifier requires actor name resolution. Generalise
if a future notifier requires the same capability.

---

## SPI Interface

`api/spi/PiIdentityResolver.java` (import: `io.quarkus.runtime.LaunchMode` for default impl):

```java
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
 */
public interface PiIdentityResolver {
    String resolveFormalName(String actorId);
}
```

**Placement:** `api/spi/` per `consumer-spi-placement.md`.

---

## Default Implementation

`runtime/service/DefaultPiIdentityResolver.java`:

```java
@ApplicationScoped @DefaultBean
public class DefaultPiIdentityResolver implements PiIdentityResolver {

    @PostConstruct
    void warnIfDefaultActive() {
        if (!LaunchMode.current().isDevOrTest()) {
            Log.warn("PiIdentityResolver: using default passthrough — sponsor notifications " +
                     "will contain raw PI actor IDs. Provide a custom implementation.");
        }
    }

    @Override
    public String resolveFormalName(String actorId) { return actorId; }
}
```

Profile guard (`LaunchMode.current().isDevOrTest()`) prevents WARN noise in `@QuarkusTest` runs.

---

## Resolution Point: SponsorNotificationListener

`PiIdentityResolver` injected into `SponsorNotificationListener`. Resolution happens inside
the existing outer `try/catch`, after skip-path guards, with its own inner try/catch to produce
a distinct audit role from delivery failures. Full method skeleton:

```java
@Transactional
public void onDeviationResolved(@ObservesAsync ProtocolDeviationResolvedEvent event) {
    if (event.escalationRequirement() != EscalationRequirement.SPONSOR_NOTIFICATION) return;

    try {
        TrialSite site = ...; // existing skip-path guards unchanged
        ClinicalTrial trial = ...; // existing skip-path guards unchanged

        // Resolution — inside outer try/catch; inner try/catch for distinct audit entry
        String piDisplayName = null;
        if (event.piId() != null) {
            try {
                piDisplayName = piIdentityResolver.resolveFormalName(event.piId());
            } catch (Exception resolveEx) {
                Log.errorf(resolveEx, "PI identity resolution failed for deviation %s", event.deviationId());
                try {
                    deviationLedgerWriter.writeSkippedSponsorEntry(event.deviationId(), event.siteId(),
                        event.severity(), clock.instant(), "sponsor-notifier-pi-resolver-failed");
                } catch (Exception writeEx) {
                    Log.errorf(writeEx, "AUDIT GAP: could not write resolver-failed entry for deviation %s",
                        event.deviationId());
                }
                return;
            }
        }

        // Delivery — existing code, updated request construction
        sponsorNotifier.notify(new SponsorNotificationRequest(
            site.trialId, event.siteId(), event.deviationId(), event.deviationType(),
            event.severity(), event.terminalStatus(),
            event.piId(), piDisplayName,
            trial.sponsorNotificationConnectorId, trial.sponsorNotificationDestination
        ));

    } catch (Exception e) {
        // existing observer failure path unchanged
        Log.errorf(e, "Unexpected error ...");
        try { deviationLedgerWriter.writeObserverFailureEntry(...); } catch (...) { ... }
    }
}
```

Resolver throws → `sponsor-notifier-pi-resolver-failed` audit entry, return.
Notifier throws → existing `writeObserverFailureEntry` with `sponsor-notifier-observer-failed`.
The two failure modes are auditably distinct by `actorRole`.

---

## SponsorNotificationRequest — new field

Add `String piDisplayName` (nullable — null for EXPIRED):

```java
public record SponsorNotificationRequest(
    UUID trialId,
    UUID siteId,
    UUID deviationId,
    String deviationType,
    DeviationSeverity severity,
    PiApprovalStatus terminalStatus,
    String piId,
    String piDisplayName,   // new — null when terminalStatus == EXPIRED
    String sponsorNotificationConnectorId,
    String sponsorNotificationDestination
) {}
```

`DefaultSponsorNotifier.buildBody()` uses `req.piDisplayName()` directly — no SPI injection.

---

## Ledger Recording

### Schema: V1014 migration (qhorus datasource)

```sql
ALTER TABLE protocol_deviation_ledger_entry
    ADD COLUMN pi_display_name VARCHAR(255);
```

### Entity: ProtocolDeviationLedgerEntry — add field

```java
@Column(name = "pi_display_name")
public String piDisplayName;
```

### writeSponsorNotifiedEntry — updated signature

```java
public void writeSponsorNotifiedEntry(ProtocolDeviation dev, Instant notifiedAt,
                                      boolean delivered, String piId, String piDisplayName)
```

Both `piId` and `piDisplayName` nullable (null for EXPIRED). Set `entry.piId` and
`entry.piDisplayName` from parameters.

### recordAttempt in DefaultSponsorNotifier — updated call

`recordAttempt(SponsorNotificationRequest req, boolean delivered)` forwards from request:

```java
ledgerWriter.writeSponsorNotifiedEntry(dev, clock.instant(), delivered,
    req.piId(), req.piDisplayName());
```

---

## All SponsorNotificationRequest Construction Sites

| File | Change |
|------|--------|
| `SponsorNotificationListener` | add `piDisplayName` (resolved above) after `piId` |
| `DefaultSponsorNotifierTest.request()` helper | add `piDisplayName` parameter after `piId` |
| `DefaultSponsorNotifierTest` inline calls (expired, title tests) | add `null` for piDisplayName |

---

## Tests

### DefaultPiIdentityResolverTest (Mockito, no Quarkus)
1. `resolveFormalName("claude:pi@v1")` returns `"claude:pi@v1"` unchanged

### DefaultSponsorNotifierBodyTest (new class, Mockito, no Quarkus)

Tests 2–4 cover pure `buildBody()` string formatting — no I/O, no container needed:

2. ESCALATED body uses `req.piDisplayName()` — assert `.contains("Dr. Jane Smith")` when piDisplayName is `"Dr. Jane Smith"`
3. REJECTED body uses `req.piDisplayName()`
4. EXPIRED body does not reference piDisplayName (system-initiated expiry carries no PI actor)

### DefaultSponsorNotifierTest (existing, @QuarkusTest)

5. Move "recordAttempt forwards piId and piDisplayName" here — `@InjectMock DeviationLedgerWriter`.
   Build request with `piId="dr-smith@v1"`, `piDisplayName="Dr. Smith"`, stub connector to succeed,
   verify `writeSponsorNotifiedEntry(any(), any(), eq(true), eq("dr-smith@v1"), eq("Dr. Smith"))`.

Update existing `escalated_notification_sends_to_connector_and_writes_delivered_ledger_entry`:
- Request helper returns `piDisplayName = "Dr. Jane Smith"` (distinguished from raw `"dr-smith@v1"`)
- Assert body `.contains("Dr. Jane Smith")` — not the raw ID

### SponsorNotificationListenerTest (existing, @QuarkusTest)

Add `@InjectMock PiIdentityResolver piIdentityResolver`.

Update existing `sponsor_notification_event_calls_spi_with_correct_request`:
- Stub `when(piIdentityResolver.resolveFormalName("dr-smith@v1")).thenReturn("Dr. Smith")`
- Assert `req.piDisplayName()` equals `"Dr. Smith"`

New tests:
6. EXPIRED event: `piIdentityResolver.resolveFormalName` NOT called (piId null for EXPIRED)
7. Resolver throws: `writeSkippedSponsorEntry` called with role `"sponsor-notifier-pi-resolver-failed"`; `sponsorNotifier.notify` NOT called

### SponsorNotificationIntegrationTest (existing, @QuarkusTest)

Add `@InjectMock PiIdentityResolver piIdentityResolver`.

Add to `@BeforeEach setUp()`:
```java
when(piIdentityResolver.resolveFormalName("dr-jones@v1")).thenReturn("Dr. Jones");
```
(All tests in the class get the stub; unstubbed mock would return null, corrupting bodies.)

Update stubs and verifies to 5-arg `writeSponsorNotifiedEntry`:
```java
doNothing().when(ledgerWriter).writeSponsorNotifiedEntry(any(), any(Instant.class), any(Boolean.class), any(), any());
Mockito.verify(ledgerWriter, ...).writeSponsorNotifiedEntry(any(), any(Instant.class), Mockito.eq(false), any(), any());
```

Update body assertions in both notification tests:
- ESCALATED: `.contains("Dr. Jones")` (not raw ID)
- REJECTED: `.contains("Dr. Jones")` (not raw ID) + `.contains("refused to authorise")`

---

## Out of Scope

- `DurableSponsorNotifier` (#21) — PiIdentityResolver injection is in the listener; notifier gets resolved name via request field automatically
- Moving `SponsorNotifier`/`SafetyOfficerNotifier` to `api/spi/` — pre-existing inconsistency, tracked separately
- Latency enforcement (timeout, fallback on breach) — advisory contract; deployer responsibility
