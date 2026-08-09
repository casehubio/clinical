# DSMB WorkItem for Batch-Detected Safety Signals

**Issue:** casehubio/clinical#146
**Epic:** casehubio/clinical#115 (CBR roadmap)
**Date:** 2026-08-07
**Status:** Design approved

## Summary

Wire WorkItem creation and direct connector notification into `TrialSafetyAggregationJob` for batch-detected safety signals (grade-threshold, cross-site-cluster). The aggregation job already detects signals, persists `TrialSafetySignal` records, fires `DsmbSafetySignalEvent` for ledger audit, and stores CBR cases. This work adds the human review gate: a DSMB WorkItem with configurable SLA, a Slack/connector notification, and a notification ledger entry.

## Relationship to Layer 6 Real-Time DSMB

Two complementary DSMB paths:

| Path | Mechanism | Detects | WorkItem creation |
|------|-----------|---------|-------------------|
| Layer 6 (existing) | Real-time blackboard flags + engine `contextChange` binding | Concurrent acute Grade 4+ AEs at ≥2 sites | Engine humanTask — automatic |
| Batch (this work) | `TrialSafetyAggregationJob` (24h periodic) | Statistical trends: grade-threshold rates, cross-site event type clusters | `WorkItemService.create()` — application-level |

These detect genuinely different patterns. Slow accumulation of Grade 3 AEs across sites is invisible to Layer 6 but caught by the batch aggregation job.

## Design Approach

**Approach B — TrialSafetySignal-driven with workItemId tracking.** WorkItem creation is co-located with signal detection in the aggregation job. `TrialSafetySignal.workItemId` provides trivial idempotency. No CDI async observer ordering concerns.

### Why not the platform SubscriptionEngine?

The platform notification infrastructure (SubscriptionEngine, NotificationDispatcher, DigestBuffer) is available but designed for user-configurable subscription matching and multi-channel delivery with preferences. This use case is simpler: a single system-initiated notification to a fixed DSMB channel when a batch signal is detected. The direct connector pattern (following `DefaultSafetyOfficerNotifier`) is the right fit. Platform subscription integration is a natural follow-up if DSMB members need per-user channel preferences or digest batching.

## Domain Model Change

### `TrialSafetySignal` — one new field

```java
@Column(name = "work_item_id")
public UUID workItemId;  // null until WorkItem created; tracks open DSMB WorkItem
```

### Migration

`V129__trial_safety_signal_work_item_id.sql` (next available V in `db/migration/default/`):

```sql
ALTER TABLE trial_safety_signal ADD COLUMN work_item_id UUID;
CREATE UNIQUE INDEX idx_trial_safety_signal_unique
    ON trial_safety_signal(trial_id, signal_type, tenant_id);
```

Nullable column — existing records have no WorkItem. The unique index enforces at most one record per (trial_id, signal_type, tenant_id) triple, preventing duplicate signal records under concurrent aggregation runs.

## WorkItem Creation

### Transaction boundary — two-phase split

Signal persistence and WorkItem creation are in **separate transactions** for error isolation. If WorkItem creation fails, the signal record is still persisted.

### Flow

```
upsertSignalRecord(trialId, signal, tenantId):
  Phase 1 — REQUIRES_NEW transaction:
    existing = findByTrialAndType(trialId, signalType, tenantId)
    if existing != null:
      update lastDetectedAt, affectedSiteCount, summary, clear resolvedAt
      needsWorkItem = (existing.workItemId == null)
                      || workItemService.findById(existing.workItemId)
                           .map(wi -> wi.status.isTerminal()).orElse(true)
      return { signalId: existing.id, needsWorkItem }
    else:
      insert new TrialSafetySignal record
      return { signalId: record.id, needsWorkItem: true }

  Phase 2 — if needsWorkItem, try-catch:
    REQUIRES_NEW transaction:
      create WorkItem via WorkItemService
      update TrialSafetySignal.workItemId = wi.id
    on success:
      notify (outside transaction — see Notification)
      write notification ledger entry
    on failure:
      LOG.warnf — signal record persisted, WorkItem deferred to next run
```

### WorkItem shape

```java
workItemService.create(WorkItemCreateRequest.builder()
    .title("DSMB review — batch safety signal: " + signalType)
    .description(summary + ". Detected by trial safety aggregation job.")
    .types(List.of("dsmb-batch-signal"))
    .formKey("dsmb-batch-signal-review")
    .priority(WorkItemPriority.HIGH)
    .candidateGroups("dsmb")
    .createdBy(ClinicalActors.CLINICAL_SERVICE)
    .callerRef("clinical:trial-safety-signal/" + signalId)
    .payload(jsonPayload(trialId, signalType, affectedSites, summary))
    .claimDeadline(clock.instant().plus(batchSignalSla))
    .expiresAt(clock.instant().plus(batchSignalExpiry))
    .build());
```

### Payload

```json
{
  "trialId": "<UUID>",
  "signalType": "GRADE_THRESHOLD | CROSS_SITE_CLUSTER",
  "affectedSiteCount": 4,
  "summary": "4 of 12 sites show Grade 3+ AE rate above 10%",
  "affectedSites": ["<UUID>", "..."]
}
```

### Idempotency

- **Signal persists, open WorkItem exists:** skip — no duplicate.
- **Signal persists, WorkItem is terminal:** create a new WorkItem. Terminal statuses are checked via `status.isTerminal()` — never enumerated in consumer code.
- **Signal resolved then re-appears:** `resolvedAt` is cleared by the existing update path. `workItemId` from the previous signal lifecycle may be terminal or null — either way, a new WorkItem is created.
- **Signal resolves, WorkItem still open:** WorkItem stays open. DSMB must explicitly review and close. GCP: safety events require documented review regardless of resolution.
- **24h blind spot on re-detection:** If a signal resolves and re-appears while the original WorkItem is still open, no new WorkItem is created. The DSMB completes the original review unaware of re-detection. The signal self-heals on the next batch run (new WorkItem created since the old one is now terminal). Acknowledged as acceptable for a 24h batch cadence.

### WorkItem status check

`workItemService.findById(workItemId)` — returns `Optional<WorkItem>`. Check `wi.status.isTerminal()`. If the WorkItem is not found (deleted, purged), treat as terminal — create a new one.

## Configuration

```properties
# SLA (claim deadline) for batch-detected DSMB WorkItems (default 72h)
casehub.clinical.dsmb.batch-signal.sla=PT72H

# Expiry for batch-detected DSMB WorkItems (default 14 days)
casehub.clinical.dsmb.batch-signal.expiry=P14D

# Connector ID for DSMB notifications (default "slack")
casehub.clinical.dsmb.batch-signal.connector-id=slack

# Notification channel/destination (default "dsmb")
casehub.clinical.dsmb.batch-signal.notification-channel=dsmb
```

The SLA default of 72h is longer than the acute Layer 6 DSMB WorkItem (48h) — batch signals are statistical trends, not immediate emergencies, but still within GCP reporting windows. The expiry of 14 days gives DSMB adequate review time without silent auto-expiry.

## Notification

### `DsmbBatchSignalNotifier`

`@ApplicationScoped`. Injected into `TrialSafetyAggregationJob`. Called **after** the WorkItem creation transaction commits successfully — never inside a DB transaction (avoids holding Agroal connections during external HTTP calls and phantom notifications on rollback).

Follows `DefaultSafetyOfficerNotifier` pattern: injects `@All List<Connector>`, resolves the target connector by ID from config, constructs `ConnectorMessage`.

```java
@ApplicationScoped
public class DsmbBatchSignalNotifier {

    @Inject @All List<Connector> connectors;
    @Inject DsmbBatchSignalNotificationLedgerWriter ledgerWriter;

    @ConfigProperty(name = "casehub.clinical.dsmb.batch-signal.connector-id",
                    defaultValue = "slack")
    String connectorId;

    @ConfigProperty(name = "casehub.clinical.dsmb.batch-signal.notification-channel",
                    defaultValue = "dsmb")
    String channel;

    public void notify(UUID trialId, String signalType, String summary,
                       int affectedSiteCount, UUID workItemId) {
        Connector connector = connectors.stream()
            .filter(c -> c.id().equals(connectorId))
            .findFirst().orElse(null);
        if (connector == null) {
            LOG.warnf("No connector with id '%s' — DSMB notification skipped", connectorId);
            ledgerWriter.writeFailure(trialId, signalType, workItemId,
                "No connector: " + connectorId);
            return;
        }
        ConnectorMessage message = new ConnectorMessage(
            channel,
            "DSMB Batch Signal: " + signalType,
            "%s\nAffected sites: %d\nTrial: %s\nWorkItem: %s"
                .formatted(summary, affectedSiteCount, trialId, workItemId));
        try {
            connector.send(message);
            ledgerWriter.writeSuccess(trialId, signalType, workItemId);
        } catch (Exception e) {
            LOG.warnf(e, "DSMB batch signal notification failed for trial %s", trialId);
            ledgerWriter.writeFailure(trialId, signalType, workItemId, e.getMessage());
        }
    }
}
```

### `DsmbBatchSignalNotificationLedgerWriter`

`@ApplicationScoped`. Writes a ledger entry for every notification attempt (success or failure) for GCP audit compliance. Follows `SafetyOfficerNotificationLedgerWriter` pattern.

Fields: `trialId`, `signalType`, `workItemId`, `notificationOutcome` (SUCCESS/FAILURE), `failureReason` (nullable).

### Parameter types

The notifier accepts primitive fields (`UUID trialId`, `String signalType`, `String summary`, `int affectedSiteCount`, `UUID workItemId`) — not `DetectedSignal`, which is package-private to `io.casehub.clinical.cbr`. The aggregation job extracts these fields before calling the notifier.

## Aggregation Job Changes

### New injections

- `WorkItemService` — WorkItem creation and status lookup
- `DsmbBatchSignalNotifier` — notification
- `ObjectMapper` — payload serialization
- `@ConfigProperty casehub.clinical.dsmb.batch-signal.sla` as `Duration batchSignalSla`
- `@ConfigProperty casehub.clinical.dsmb.batch-signal.expiry` as `Duration batchSignalExpiry`

### Modified method

`upsertSignalRecord()` — split into two phases (see Flow above). Phase 1 persists the signal record. Phase 2 creates the WorkItem in a separate transaction with try-catch error isolation. Notification fires after Phase 2 commits.

### Unchanged

- `fireSignalEvent()` — CDI event still fires for `DsmbSafetySignalLedgerWriter` (audit trail)
- `resolveStaleSignals()` — resolved signals keep their `workItemId`; WorkItem stays open
- `storeCbrCase()` — CBR storage unchanged
- `detectSignals()`, `detectGradeThreshold()`, `detectCrossSiteCluster()` — detection logic unchanged

## Test Strategy

### Unit tests

**`TrialSafetyAggregationJobWorkItemTest`** — Mockito-based, no Quarkus:
- New signal → WorkItem created, `workItemId` set on `TrialSafetySignal`, notifier called
- Signal persists with open WorkItem → no duplicate WorkItem, notifier not called
- Signal persists with terminal WorkItem → new WorkItem created (re-escalation)
- Signal resolved then re-appears → new WorkItem created
- WorkItem creation failure → logged, signal record still persisted (error isolation — Phase 1 committed independently)
- Notification failure → logged, WorkItem still created (notifier is post-commit)

**`DsmbBatchSignalNotifierTest`** — Mockito-based:
- Verify ConnectorMessage construction (destination, title, body)
- Connector resolved by ID from list
- Missing connector → warning logged, failure ledger entry written
- Connector failure → warning logged, failure ledger entry written
- Success → success ledger entry written

### Integration tests (`@QuarkusTest`)

**`DsmbBatchSignalWorkItemTest`:**
- Seed a trial with enough sites and AE data to trigger a grade-threshold signal
- Call `aggregateTrial()` directly
- Assert: `TrialSafetySignal` record has non-null `workItemId`, WorkItem queryable via `WorkItemQueries` with title containing "DSMB review" and "batch safety signal", `callerRef` matches signal ID
- Call `aggregateTrial()` again with same data → no second WorkItem created (idempotency)
- Complete the WorkItem, call `aggregateTrial()` again → new WorkItem created (re-escalation)

## Files Changed

| File | Change |
|------|--------|
| `runtime/.../entity/TrialSafetySignal.java` | Add `workItemId` field |
| `runtime/src/main/resources/db/migration/default/V129__*.sql` | New migration (column + unique index) |
| `runtime/.../cbr/TrialSafetyAggregationJob.java` | Two-phase WorkItem creation in `upsertSignalRecord()` |
| `runtime/.../service/DsmbBatchSignalNotifier.java` | New — connector notification (follows DefaultSafetyOfficerNotifier) |
| `runtime/.../service/DsmbBatchSignalNotificationLedgerWriter.java` | New — notification audit trail |
| `runtime/.../ledger/DsmbBatchSignalNotificationLedgerEntry.java` | New — ledger entry subclass |
| `runtime/src/main/resources/application.properties` | Add SLA, expiry, connector-id, channel config |
| `runtime/src/test/resources/application.properties` | Add test config |
| `runtime/src/test/.../cbr/TrialSafetyAggregationJobWorkItemTest.java` | New — unit tests |
| `runtime/src/test/.../service/DsmbBatchSignalNotifierTest.java` | New — unit tests |
| `runtime/src/test/.../cbr/DsmbBatchSignalWorkItemTest.java` | New — integration test |

## Review Findings Addressed

| Finding | Resolution |
|---------|-----------|
| Transaction boundary / error isolation | Split into two-phase flow — signal persistence and WorkItem creation in separate transactions |
| Notification inside transaction / phantom notifications | Notification fires after WorkItem transaction commits |
| Connector API mismatch | `@All List<Connector>`, resolve by ID, use `ConnectorMessage` |
| Missing unique constraint | Added to V129 migration |
| WorkItemStore layer violation | Use `workItemService.findById()` |
| Missing notification ledger audit | Added `DsmbBatchSignalNotificationLedgerWriter` |
| Missing callerRef | Added `clinical:trial-safety-signal/{signalId}` |
| No explicit expiresAt | Added configurable expiry (default 14 days) |
| ESCALATED terminal status error | Use `status.isTerminal()` — no enumeration |
| Platform notification rationale | Documented in Design Approach section |
| DetectedSignal visibility | Notifier accepts primitive fields, not package-private type |
