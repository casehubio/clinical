# CBR Phase 7: Multi-Scope Memory for DSMB Pattern Detection

**Issue:** casehubio/clinical#120
**Epic:** casehubio/clinical#115 (CBR roadmap, Phase 7)
**Date:** 2026-07-30
**Status:** Design approved

## Summary

Wire neocortex's existing scope hierarchy, scope decay, trust weighting, temporal decay, supersession, and retention APIs into clinical's CBR layer. Build a cross-scope aggregation job that materializes trial-level safety signals from site-level AE data. Add GDPR-compliant patient-scope erasure to `ConsentWithdrawalService`.

Neocortex already provides: `Path` hierarchy, `ScopeDecay` strategies, `TrustWeightedCbrCaseMemoryStore`, `TemporalDecay`, supersession, `purge()`, `eraseByScope()`. Clinical currently ignores all of these, passing `Path.root()` everywhere. This work wires them in.

## Scope Model

### Hierarchy

Three-level positional hierarchy: `trial → site → patient`.

```
Path.of(trialId)                          // depth 1 — trial scope
Path.of(trialId, siteId)                  // depth 2 — site scope
Path.of(trialId, siteId, patientId)       // depth 3 — patient scope
```

No labeled segments — depth encodes level. `Path.isAncestorOf()` provides visibility: ancestor-scope cases are visible at descendant query scopes, not vice versa.

### ClinicalScope Enum

Module: `api/` (pure Java, follows `CtcaeGrade`, `DeviationSeverity` pattern).

```java
public enum ClinicalScope {
    TRIAL(1),
    SITE(2),
    PATIENT(3);

    private final int depth;
}
```

### ClinicalScopeResolver

Module: `runtime/` (depends on Panache entities for navigation).

`@ApplicationScoped` utility — single source of truth for scope assignment. Writers never construct `Path` directly.

| Method | Returns | Path shape |
|--------|---------|------------|
| `forAdverseEvent(AdverseEvent)` | `Optional<Path>` at PATIENT depth | `Path.of(trialId, siteId, patientId)` |
| `forDeviation(ProtocolDeviation)` | `Optional<Path>` at SITE depth | `Path.of(trialId, siteId)` |
| `forAmendment(ProtocolAmendment)` | `Optional<Path>` at TRIAL depth | `Path.of(trialId)` |
| `forSiteEnrollment(TrialSite)` | `Optional<Path>` at SITE depth | `Path.of(trialId, siteId)` |
| `forTrial(ClinicalTrial)` | `Optional<Path>` at TRIAL depth | `Path.of(trialId)` |

**Entity traversal:** `forAdverseEvent(ae)` navigates `ae.enrollmentId → PatientEnrollment → enrollment.siteId → TrialSite → site.trialId` to construct the full scope path. Each entity lookup may return null (entity not found). FK columns (`enrollmentId`, `siteId`, `trialId`) are `NOT NULL` in the schema, so null lookups indicate data integrity issues, not expected states.

**Null contract:** All methods return `Optional.empty()` when any entity in the navigation chain is not found. Writers skip CBR storage when scope is unavailable — this is consistent with `ClinicalCaseOutcomeObserver`'s existing null-guard pattern. `Optional.empty()` is strictly better than `Path.root()` fallback, which would make the case visible from all scopes.

### Domain → Scope Mapping

| Domain | Scope | Rationale |
|--------|-------|-----------|
| `clinical-ae` | PATIENT | AE is a patient-level event |
| `clinical-ae-trajectory` | PATIENT | Time-series of one patient's AE progression |
| `clinical-deviation` | SITE | Deviations are site-level protocol adherence |
| `clinical-amendment` | TRIAL | Amendments apply trial-wide |
| `clinical-site-enrollment` | SITE | Enrollment trajectory per site |
| `clinical-trial-safety` *(new)* | TRIAL | Materialized rollups from aggregation job |

## Writer Updates

All 5 existing writers replace `Path.root()` with `ClinicalScopeResolver` calls. Changes are mechanical — inject resolver, call appropriate method, pass result to `store()`.

- **`ClinicalCaseOutcomeObserver`** — `scopeResolver.forAdverseEvent(ae)` for AE and trajectory cases
- **`DeviationResolutionCbrWriter`** — `scopeResolver.forDeviation(deviation)`
- **`AmendmentResolutionCbrWriter`** — `scopeResolver.forAmendment(amendment)`
- **`SiteEnrollmentTrajectoryJob`** — `scopeResolver.forSiteEnrollment(site)`
- **`TrialCompletionSiteTrajectoryWriter`** — `scopeResolver.forSiteEnrollment(site)`

**`ClinicalCbrService`** — `storeIdempotent()` updated to accept `Path scope` parameter instead of hardcoding `Path.root()`. `retrieveWithAudit()` needs no signature change — callers set scope and scope decay on the `CbrQuery` via existing builder methods (`CbrQuery.withScope()`, `CbrQuery.withScopeDecay()`).

## Scope-Aware Retrieval

All 3 retrievers pass scope and scope decay on queries.

### Default Scope Decay

`ScopeDecay.Exponential(0.7)` — each scope level up retains 70% weight. Configurable per domain:

```properties
casehub.clinical.cbr.scope-decay.ae=exponential:0.7
casehub.clinical.cbr.scope-decay.deviation=exponential:0.8
casehub.clinical.cbr.scope-decay.amendment=step:1.0
```

### Config Converters

The `type:value` format strings for `ScopeDecay` and `TemporalDecay` require MicroProfile Config custom converters (one `Converter<ScopeDecay>`, one `Converter<TemporalDecay>`). Both `ScopeDecay` and `TemporalDecay` are sealed interfaces with known subtypes:

- **ScopeDecay:** `exponential:<base>` → `ScopeDecay.Exponential(base)`, `linear:<maxDepth>` → `ScopeDecay.Linear(maxDepth)`, `step:<beyondExact>` → `ScopeDecay.Step(beyondExact)`
- **TemporalDecay:** `halflife:<duration>` → `TemporalDecay.HalfLife(duration)`, `linear:<zeroAt>` → `TemporalDecay.Linear(zeroAt)`, `step:<cutoff>:<afterCutoff>` → `TemporalDecay.Step(cutoff, afterCutoff)`

Duration values use ISO-8601 shorthand: `90d` → `Duration.ofDays(90)`, `365d` → `Duration.ofDays(365)`.

These converters belong in `runtime/` (clinical-specific config parsing). They are registered via `@RegisterConverter` or `META-INF/services`.

### Retriever Scope Visibility

**`AeEscalationPlanRetriever`** — queries at patient scope. Sees:
- Patient's own AE cases at full weight (distance 0)
- Trial-level safety aggregates at 0.49 weight (distance 2)
- Other patients' cases at same site are NOT visible (stored at their own patient scope, not an ancestor)

Cross-patient patterns surface through trial-level aggregates.

**`AeTrajectoryAlertService`** — queries at patient scope. Same visibility rules.

**`SiteEnrollmentAlertService`** — queries at site scope. Sees site cases at full weight, trial cases at 0.7 weight.

## Cross-Scope Aggregation

### New Domain

`ClinicalCbrDomains.TRIAL_SAFETY` — `MemoryDomain.of("clinical-trial-safety")`.

### New Schema

Registered in `ClinicalCbrSchemaInitializer`:
- `trialPhase` (categorical)
- `aggregationPeriodDays` (numeric)
- `siteCount` (numeric)
- `affectedSiteCount` (numeric)
- `dominantGrade` (numeric 1-5)
- `dominantEventType` (categoricalList)
- `signalType` (categorical: `grade-threshold`, `frequency-spike`, `cross-site-cluster`)

### TrialSafetyAggregationJob

`@Scheduled`, configurable interval (default 24h). Tenant scoping via config property (follows `SiteEnrollmentTrajectoryJob` pattern):

```properties
casehub.clinical.trial-safety-aggregation.tenant-id=default
casehub.clinical.trial-safety-aggregation.interval=24h
```

For each active trial (in the configured tenant):

1. Query `AdverseEvent` JPA entities grouped by site (domain data, not CBR store — `CbrCaseMemoryStore` has no scan/enumerate API, and similarity matching is wrong for batch aggregation)
2. Compute trial-level signals:
   - **Grade threshold** — proportion of sites with Grade 3+ AE rate above configurable threshold
   - **Frequency spike** — sites where AE count per enrollment exceeds trial-wide mean by > 2σ
   - **Cross-site cluster** — same event type appearing at ≥ N sites (configurable, default 3)
3. Store each detected signal as `PlanCbrCase` at trial scope in `clinical-trial-safety` domain. Entity ID is deterministic: `trial-{trialId}-{signalType}` (one CBR case per signal type per trial, replaced each run via `storeIdempotent`).
4. Upsert each detected signal into `TrialSafetySignal` JPA entity. Fire `DsmbSafetySignalEvent` only on **insert** (new signal not previously detected) or when `affectedSiteCount` increases (signal escalation).

### Signal State Tracking

`TrialSafetySignal` — JPA entity in `runtime/`, table `trial_safety_signal`:

| Column | Type | Purpose |
|--------|------|---------|
| `id` | UUID | PK |
| `tenant_id` | VARCHAR | Tenant scoping |
| `trial_id` | UUID | FK to trial |
| `signal_type` | VARCHAR | `grade-threshold`, `frequency-spike`, `cross-site-cluster` |
| `affected_site_count` | INT | Number of sites exhibiting the signal |
| `first_detected_at` | TIMESTAMP | When this signal was first detected |
| `last_detected_at` | TIMESTAMP | Updated each run the signal persists |
| `resolved_at` | TIMESTAMP | Set when the signal is no longer detected; nullable |

Unique constraint on `(tenant_id, trial_id, signal_type)`. The job upserts: if no row exists, INSERT and fire `DsmbSafetySignalEvent`. If row exists with `resolved_at` not null (previously resolved, now re-detected), clear `resolved_at`, update `last_detected_at`, and fire. If row exists and `affected_site_count` increased, update and fire (escalation). Otherwise, update `last_detected_at` only — no event fired.

Signals no longer detected in the current run have `resolved_at` set to now. This provides a complete lifecycle: detection → persistence → resolution → possible re-detection.

### Relationship to Layer 6 Real-Time DSMB

The existing Layer 6 mechanism (`TrialSafetySignalService` + `trial-coordination.yaml` DSMB binding) provides **real-time** detection: site-level `grade4Active.<siteId>` boolean flags accumulate on the trial case's blackboard, and the DSMB binding fires when ≥2 sites simultaneously have active Grade 4+ AE cases. This is event-driven and fires immediately.

`TrialSafetyAggregationJob` is **complementary**, not overlapping:
- **Layer 6 (existing):** detects concurrent acute signals — "two sites have Grade 4+ AEs right now." Boolean flags, event-driven, instant.
- **Aggregation job (new):** detects statistical trends — frequency spikes (AE rate above 2σ), cross-site clusters (same event type at ≥3 sites), grade threshold rates (proportion of sites above a Grade 3+ rate). Batch, periodic, trend-based.

These detect genuinely different patterns. Slow accumulation of Grade 3 AEs at a site is invisible to Layer 6 (no Grade 4+ flag) but visible to the aggregation job's frequency spike detection.

`DsmbSafetySignalEvent` does NOT write to the trial blackboard or trigger the existing DSMB binding. It is a separate CDI event channel.

### DsmbSafetySignalEvent

Module: `api/` (pure Java CDI event, follows `ProtocolDeviationResolvedEvent` and `AeEscalationCompletedEvent` pattern).

```java
public record DsmbSafetySignalEvent(UUID trialId, String signalType,
                                     List<UUID> affectedSites, String summary,
                                     String tenantId) {}
```

### DsmbSafetySignalLedgerWriter

Module: `runtime/`. `@ApplicationScoped`, observes `@ObservesAsync DsmbSafetySignalEvent`. Writes a tamper-evident ledger entry recording the safety signal detection. This is the minimum viable consumer — every safety signal must be captured in the audit trail for FDA/GCP compliance, regardless of whether notification or WorkItem consumers exist.

Follows the existing `CbrRetrievalLedgerWriter` pattern: creates a ledger entry with `entryType = LedgerEntryType.EVENT`, `actorType = ActorType.SYSTEM`, and the signal details in structured fields.

WorkItem creation for batch-detected statistical trends (analogous to the Layer 6 DSMB WorkItem for acute Grade 4+ signals) is deferred — it depends on the notification infrastructure design and is tracked as a follow-up issue (see §Follow-Up Issues).

## Trust Wiring

### ClinicalAgentTrustProvider

`@ApplicationScoped`, implements `AgentTrustProvider`. Overrides the engine's `@DefaultBean` `TrustScoreAgentTrustProvider` (which returns `TrustScoreSource.globalScore(agentId)` — a single scalar across all dimensions).

`ClinicalAgentTrustProvider` injects `TrustScoreSource` (ledger SPI, implemented by `CachedTrustScoreSource` / `ComputedTrustScoreSource` / `MaterializedTrustScoreSource`) and calls `dimensionScore(agentId, dimension)` for each clinical trust dimension (`safety-accuracy`, `eligibility-precision`, `protocol-adherence`), averaging the non-empty results. Returns `OptionalDouble.empty()` when no dimension scores are available.

This is distinct from Layer 7 trust routing (which is stub). Trust routing uses trust scores to select which agent handles a task. CBR trust weighting uses trust scores to weight the confidence of CBR cases produced by that agent. The trust score computation infrastructure (`TrustScoreSource` SPI and its implementations) is fully built and shared by both use cases.

### Writer Updates — Trust Integration

Each writer extracts `producerAgentId` where available and looks up the current trust score via `ClinicalAgentTrustProvider`. Both fields are passed to the `PlanCbrCase` constructor (which accepts null for both).

| Writer | `producerAgentId` source | Null when |
|--------|--------------------------|-----------|
| `ClinicalCaseOutcomeObserver` | `PlanItemRecord.executorName()` for the `safety-review` binding (filtered via existing `BINDING_CAPABILITY_MAP`) | Grade 1/2 AE with no safety-review binding, or binding had no executor |
| `DeviationResolutionCbrWriter` | null (always) | PI-driven workflow — the PI is a human, not an agent. `PlanTrace` already records null worker name. |
| `AmendmentResolutionCbrWriter` | Navigate `ProtocolAmendment.engineCaseId → PlanItemStore.findByCaseId()` → executor name from the amendment-advisor binding | Amendment without engine case, or advisor binding had no executor |

**Null `producerAgentId` behavior:** `TrustWeightedCbrCaseMemoryStore` calls `AgentTrustProvider.currentTrustScore(producerAgentId)`. When `producerAgentId` is null, the provider returns `OptionalDouble.empty()`, and no trust modulation is applied — the case stores with its original confidence. This is correct for human-driven workflows (PI deviations, manual amendments).

### Configuration

```properties
casehub.cbr.trust-weighting.enabled=true
casehub.cbr.trust-weighting.influence=0.3
```

## Active Memory Management

### Temporal Decay

All retrievers pass `TemporalDecay` from config. Default half-lives per domain:

```properties
casehub.clinical.cbr.temporal-decay.ae=halflife:90d
casehub.clinical.cbr.temporal-decay.ae-trajectory=halflife:60d
casehub.clinical.cbr.temporal-decay.deviation=halflife:180d
casehub.clinical.cbr.temporal-decay.amendment=halflife:365d
casehub.clinical.cbr.temporal-decay.site-enrollment=halflife:60d
casehub.clinical.cbr.temporal-decay.trial-safety=halflife:90d
```

### Supersession Hooks

One clinical supersession trigger, as a CDI observer in the `cbr` package:

1. **Amendment supersession** (`ProtocolAmendmentResolvedEvent`) — supersede prior amendment CBR cases for the same trial. The hook queries `ProtocolAmendment.findByTrialId(event.trialId())` to discover prior amendments ordered by `proposedAt`, then supersedes any existing CBR case for the most recent prior amendment (entity ID `priorAmendment.id.toString()` in `clinical-amendment` domain). No version-chaining field is needed — temporal ordering by `proposedAt` within the same trial is sufficient.

**Why no IRB supersession hook:** `DeviationResolutionCbrWriter` already observes both `ProtocolDeviationResolvedEvent` and `IrbApprovalResolvedEvent`, calling `storeIdempotent()` (erase-before-store) on each. The PI observer stores an incomplete case (`irbDecision = "N/A"` for CRITICAL deviations). The IRB observer overwrites it with the complete case. A separate supersession hook on `IrbApprovalResolvedEvent` would race with the writer (CDI `@ObservesAsync` ordering is non-deterministic) — either finding no prior case (writer erased it) or superseding a case about to be erased. Erase-before-store is correct here: the PI-only intermediate case is not useful precedent and has no audit value as a superseded record. The current entity model (`IrbApproval.find("deviationId", deviationId).firstResult()`) supports one IRB review per deviation — there is no "overturn" scenario to model.

**Deferred: AE regrade supersession.** AE regrading (changing an AE's CTCAE grade after initial assessment) is a clinical workflow that does not yet exist — no `AeRegradedEvent`, no regrade service, no regrade API. The supersession hook (supersede original AE CBR case when grade changes) will be added when the AE regrade capability is built. File follow-up issue for AE regrade capability.

### Retention Purge

`CbrRetentionPurgeJob` — `@Scheduled` (default weekly). Runs `purge()` per domain:

```properties
casehub.clinical.cbr.retention.ae.max-age-days=730
casehub.clinical.cbr.retention.ae.max-cases=10000
casehub.clinical.cbr.retention.ae-trajectory.max-age-days=365
casehub.clinical.cbr.retention.trial-safety.max-age-days=365
```

### Compaction (deferred)

Case compaction (merging similar cases into weighted representatives) is not in scope for this spec. Temporal decay + purge handle volume growth. Compaction is tracked as a follow-up issue (see §Follow-Up Issues) — it will be filed before implementation of this spec begins.

## GDPR Scope Isolation

### Patient Consent Withdrawal

`ConsentWithdrawalService` adds CBR scope-based erasure **before** the existing pseudonymization step. The original `patientId` is needed to construct the scope path that matches stored CBR cases — after pseudonymization (`enrollment.patientId = "erased-" + UUID.randomUUID()`), the original value is irrecoverable.

**Ordering constraint:** CBR erasure must execute before line 68 (`enrollment.patientId = "erased-" + ...`). The implementation captures `enrollment.patientId` and navigates `enrollment.siteId → TrialSite.findById(enrollment.siteId) → site.trialId` before any mutation.

```java
// Before pseudonymization — capture original patientId and navigate to trialId
String originalPatientId = enrollment.patientId;
TrialSite site = TrialSite.findById(enrollment.siteId);
UUID trialId = site.trialId;

// CBR scope-based erasure
cbrCaseMemoryStore.eraseByScope(
    Path.of(trialId.toString(), enrollment.siteId.toString(), originalPatientId),
    tenantId);

// Then proceed with existing pseudonymization...
enrollment.patientId = "erased-" + UUID.randomUUID();
```

Erases all CBR cases at patient scope (AE cases, AE trajectory cases). Site-level and trial-level cases unaffected — `eraseByScope` only erases at the given scope and descendants.

### Dual Erasure Mechanisms

Both erasure calls are required and serve different stores:

1. **Existing `CaseMemoryStore.eraseEntity("patient:" + enrollmentId, tenantId)`** — erases from the general memory store. Uses enrollment ID as entity key. Remains unchanged.
2. **New `CbrCaseMemoryStore.eraseByScope(Path.of(trialId, siteId, patientId), tenantId)`** — erases from the CBR store by scope path. Required because CBR cases use entity IDs like `aeId.toString()` and `aeId + "-trajectory"` (set by `ClinicalCaseOutcomeObserver`), which don't match the general store's `"patient:" + enrollmentId` key. Scope-based erasure matches by path hierarchy, catching all CBR cases stored at the patient's scope.

### Why Aggregates Survive

Trial-level safety aggregate cases contain counts and rates (`affectedSiteCount: 3`, `dominantGrade: 4`), not patient identifiers. Stored at trial scope, not a descendant of patient scope. Statistical patterns preserved after individual erasure.

### Site-Level Cases

Not erased on patient consent withdrawal. Deviations are site-level events (PI protocol adherence). Enrollment trajectories are aggregate counts per site. Neither contains patient PII in CBR feature vectors.

### Audit

`ErasureNotificationCbrCaseMemoryStore` decorator fires `CbrCasesErased.ByScope` CDI event. Existing ledger erasure entry from `ConsentWithdrawalService` covers the audit trail.

## Migration

No migration needed. Pre-release harness — existing `Path.root()` cases don't contain production patient data. Old cases orphaned (not visible to scoped queries) is acceptable.

## Test Strategy

- **Unit tests:** `ClinicalScopeResolver` path construction, `TrialSafetyAggregationJob` signal detection logic, supersession hook triggers
- **Integration tests (`@QuarkusTest`):** end-to-end scope-tagged storage and scoped retrieval, GDPR erasure at patient scope preserving site/trial cases, trust-weighted retrieval scoring, aggregation job producing trial-level cases
- **Correctness tests:** scope decay arithmetic (exponential 0.7 at distances 0/1/2), temporal decay half-life calculations, signal detection thresholds (grade threshold, frequency spike σ, cross-site cluster count)

## Follow-Up Issues

The following issues will be filed before implementation begins:

- **Compaction:** CBR case compaction (merging similar cases into weighted representatives). Issue #120 requirement #6 includes this; deferring to a follow-up issue since temporal decay + purge handle volume in the near term. The issue must reference #120 and document the trigger criteria (retrieval performance degradation or case count threshold).
- **AE regrade capability:** AE regrading workflow (changing CTCAE grade after initial assessment), including `AeRegradedEvent` and the CBR supersession hook that supersedes the original AE case with regraded features. Blocked on the AE regrade service design.
- **DSMB WorkItem for batch signals:** WorkItem creation for batch-detected statistical trends from `TrialSafetyAggregationJob` (analogous to Layer 6's DSMB WorkItem for acute Grade 4+ concurrent signals). Depends on notification infrastructure design.
