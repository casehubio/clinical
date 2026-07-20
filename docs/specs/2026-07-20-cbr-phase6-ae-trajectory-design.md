# CBR Phase 6 — AE Progression Trajectory Monitoring

**Issue:** casehubio/clinical#119
**Branch:** `issue-119-cbr-ae-trajectory`
**Date:** 2026-07-20

## Summary

Add trajectory-based CBR to clinical: capture AE state progressions over time as `TimeSeries` features, match developing trajectories against past cases via DTW, and fire proactive alerts when a trajectory matches high-severity historical outcomes. Includes site enrollment trajectory monitoring for investigator disengagement detection.

## Core Mechanism — Lazy Trajectory Reconstruction

No new CDI events for trajectory data collection. No incremental CBR writes. Trajectories are reconstructed on demand from existing data sources (alert services fire `AeTrajectoryAlertEvent` and `SiteEnrollmentAlertEvent` as output events):

- **AE entity** — current grade, escalation status, SUSAR status, regulatory status
- **Engine PlanItemStore** — binding execution records with timestamps (safety-review, dsmb-escalation, etc.)
- **PatientEnrollment entities** — creation timestamps grouped by site and week

At case completion, the full trajectory is stored as a CBR case with TimeSeries features. During the lifecycle, partial trajectories are reconstructed and queried for proactive alerting.

## Data Model

### AE Trajectory TimeSeries

```
TimeSeries "aeTrajectory"
  timestampField: "ts" (Numeric, 0..7776000 — seconds since reportedAt, 90-day window)
  innerFields:
    - ts          (Numeric, 0..7776000)
    - escalation  (Numeric, 0..3)        — NONE=0, REQUESTED=1, COMPLETED=2, FAILED=3
    - susar       (Numeric, 0..3)        — NONE=0, REQUESTED=1, COMPLETED=2, FAILED=3
    - regulatory  (Numeric, 0..3)        — NONE=0, REQUESTED=1, FILED=2, DEADLINE_MISSED=3
  similaritySpec: DtwSpec(new SakoeChibaBand(3))
  trendSpec: { SLOPE, ACCELERATION, CHANGE_POINTS } on HOURS
```

Timestamps are relative to `reportedAt` so DTW compares trajectory shape without absolute date skew. 90-day range covers SUSAR assessment (days–weeks), DSMB review (2–4 weeks), and regulatory submission timelines (15–30+ days). TrendAnalyzer auto-derives: `aeTrajectory.escalation.slope`, `aeTrajectory.escalation.acceleration`, `aeTrajectory.escalation.changePoints` (and same for susar, regulatory).

Grade is excluded from the TimeSeries inner fields because `AdverseEvent.grade` is a fixed field — it never changes within a trajectory. Grade is captured as a scalar pre-filter feature instead. When grade regrading is implemented (#135), grade should be added back as a dynamic inner field with TrendAnalyzer enrichment.

`SakoeChibaBand(3)` constrains DTW warping to ±3 observations. For typical AE trajectories (5–10 observations), this allows ~30% temporal flexibility — matching cases with similar progression shapes even when individual steps took slightly different durations.

Stored as `FeatureValue.StructListVal` — list of observation structs, each with all inner fields.

### Site Enrollment TimeSeries

```
TimeSeries "enrollmentRate"
  timestampField: "ts" (Numeric, 0..260 — week since trial activation)
  innerFields:
    - ts              (Numeric, 0..260)
    - cumulativeCount (Numeric, 0..10000)
    - periodCount     (Numeric, 0..500)   — enrolled this week
  similaritySpec: DtwSpec(new SakoeChibaBand(3))
  trendSpec: { SLOPE, ACCELERATION, CHANGE_POINTS } on WEEKS
```

Trend enrichment gives `enrollmentRate.periodCount.slope` (deceleration) and `enrollmentRate.periodCount.acceleration`.

### Why TimeSeries Over DiscreteSequence

An escalation that progresses from REQUESTED to COMPLETED in 4 hours has a very different clinical profile than one that takes 3 weeks. DTW on TimeSeries captures both state ordering AND temporal pattern. DiscreteSequence with EditDistance loses timing.

## Components

### Trajectory Builders

**`AeTrajectoryBuilder`** (`@ApplicationScoped`, `io.casehub.clinical.cbr`)

- `buildTrajectory(AdverseEvent ae, String tenantId)` → `List<Map<String, FeatureValue>>` — full trajectory for completed cases
- `buildPartialTrajectory(AdverseEvent ae, String tenantId)` → same return type — for developing cases (fewer observations, same logic)

Reads AE entity fields + PlanItemRecords from all related engine cases when their case IDs are non-null: `ae.engineCaseId` (escalation: safety-review, dsmb-escalation), `ae.susarOversightCaseId` (SUSAR oversight: susar-assessment gate), `ae.regulatorySubmissionCaseId` (regulatory submission). Records from all cases are merged and sorted by timestamp. For Grade 1-2 AEs with no engine cases, produces a single observation from the AE entity alone.

**Temporal precision:** `PlanItemRecord` currently has only `createdAt` (step initiation time), not completion time. For "started" observations (susar=REQUESTED, regulatory=REQUESTED), `createdAt` IS the relevant timestamp. For "terminal" observations (safety review completed, SUSAR gate outcome), `createdAt` reflects when the step was initiated, not when it completed — temporal ordering is correct but inter-observation durations are approximate. When `completedAt` is added to `PlanItemRecord` (casehubio/engine#763), the trajectory builder should prefer `completedAt` for terminal plan items.

Data source mapping:

| PlanItemRecord binding | Observation state change |
|---|---|
| Initial report (always) | escalation=NONE (or REQUESTED if engine case started) |
| `safety-review` terminal | (status carry-forward, marks review milestone) |
| `susar-oversight` started | susar=REQUESTED |
| SUSAR gate terminal | susar=COMPLETED or FAILED |
| `dsmb-escalation` terminal | (marks DSMB milestone) |
| `regulatory-submission` started | regulatory=REQUESTED |
| Case completed | escalation=COMPLETED |

Each observation carries the full AE status fields at that moment — not just the delta.

**`SiteEnrollmentTrajectoryBuilder`** (`@ApplicationScoped`, `io.casehub.clinical.cbr`)

- `buildTrajectory(UUID siteId, UUID trialId, Instant trialActivatedAt, String tenantId)` → `List<Map<String, FeatureValue>>`

Queries `PatientEnrollment` for the site, groups by week since `trialActivatedAt`, produces one observation per week with cumulative and period counts.

### CBR Domains and Schema

New domains in `ClinicalCbrDomains`:
- `AE_TRAJECTORY = new MemoryDomain("clinical-ae-trajectory")`
- `SITE_ENROLLMENT = new MemoryDomain("clinical-site-enrollment")`

Separate from existing `AE` domain — different features (TimeSeries vs scalar), different queries, different use cases.

New schemas in `ClinicalCbrSchemaInitializer`:

`clinical-ae-trajectory`:
- **Filters** (hard exclusion via `CbrQuery.filters`): `eventType` — domain-incompatible cases (e.g. cardiac vs hepatic AEs) must not match
- **Features** (similarity-scored via `CbrQuery.features`): `grade`, `trialPhase`, `unexpected`, `suspected` — contribute to similarity scoring but do not exclude cases, enabling cross-grade trajectory prediction (a Grade 2 AE can match against Grade 4 case trajectories)
- **TimeSeries:** `"aeTrajectory"` with DtwSpec + TrendSpec

`clinical-site-enrollment`:
- **Filters:** `trialPhase`
- **Features:** `siteRegion` (optional)
- **TimeSeries:** `"enrollmentRate"` with DtwSpec + TrendSpec

### Alert Services

**`AeTrajectoryAlertEvent`** (record in `api/`):

```java
public record AeTrajectoryAlertEvent(
    UUID aeId, UUID enrollmentId, UUID siteId,
    CtcaeGrade currentGrade,
    int matchCount, double topScore,
    String predictedOutcome, double predictedProbability,
    String traceId, String tenantId)
```

**`AeTrajectoryAlertService`** (`@ApplicationScoped`):

`evaluate(UUID aeId, String tenantId)` → `Optional<AeTrajectoryAlertEvent>`

1. Load AE (bail if not found)
2. Build partial trajectory (single observation for Grade 1-2 with no engine case; multi-observation for Grade 3+ with engine case)
3. Build CbrQuery: `eventType` as `CbrFilter.Contains(ae.eventType)` in `filters`; `grade`, `trialPhase`, `unexpected`, `suspected` as `FeatureValue` entries in `features` (similarity-scored, not hard-filtered); trajectory as `FeatureValue.structList()` in `features`
4. Call `ClinicalCbrService.retrieveWithAudit(query, PlanCbrCase.class, ae.enrollmentId, ClinicalActors.CLINICAL_SERVICE)` — `enrollmentId` as subject (the patient enrollment under analysis), `CLINICAL_SERVICE` as actor (automated system evaluation)
5. Aggregate predicted outcome via weighted majority voting (see below)
6. If matches ≥ minMatches and predicted probability ≥ threshold, fire async CDI event

**Predicted outcome aggregation — weighted majority voting:**

1. Filter results to cases with similarity ≥ `min-similarity`
2. Group matched cases by outcome label (`CbrOutcome.label()` — e.g. "COMPLETED", "FAULTED")
3. For each outcome group, sum the similarity scores of its cases
4. `predictedOutcome` = the outcome label of the group with the highest score sum
5. `predictedProbability` = (winning group's score sum) / (total score sum across all groups)

This is standard weighted k-NN classification. Similarity-weighted voting means higher-similarity matches have proportionally more influence on the prediction.

Config:
- `casehub.clinical.trajectory.alert.min-matches` = 2
- `casehub.clinical.trajectory.alert.min-similarity` = 0.5
- `casehub.clinical.trajectory.alert.min-probability` = 0.6

**`SiteEnrollmentAlertEvent`** and **`SiteEnrollmentAlertService`** — same pattern for site enrollment. Uses `ClinicalActors.CLINICAL_SERVICE` as actor and `siteId` as subject (the site whose enrollment pattern is being evaluated).

### Lifecycle Integration

AE trajectory evaluation hooks (added to existing services):

| Service method | Hook placement | Hook |
|---|---|---|
| `AdverseEventService.reportAdverseEvent()` | After `ae.persist()` and ledger write, for ALL grades | `alertService.evaluate(aeId)` — enables predictive alerting for Grade 1-2 AEs |
| `AeEscalationCaseService.onAdverseEventReported()` | After `persistCaseId()` (Phase 3) | `alertService.evaluate(aeId)` — re-evaluates with engine case context |
| `AeEscalationListener.onCaseLifecycle()` | After `statusUpdater.markCompleted()` returns true | `alertService.evaluate(aeId)` — final trajectory state at case completion |
| `SusarOversightCaseService.onAdverseEventReported()` | After `persistCaseId()` (Phase 3) | `alertService.evaluate(aeId)` — re-evaluates with SUSAR case context |
| `SusarGateDecisionListener` (all outcomes) | After gate outcome persisted and ledger written | `alertService.evaluate(aeId)` — trajectory updated with SUSAR outcome |

Site enrollment evaluation hook:

| Path | Hook |
|---|---|
| `PatientResource.enrollPatient()` flow | `siteEnrollmentAlertService.evaluate(siteId, trialId)` after enrollment persisted |

All evaluation calls wrapped in try-catch. Failure logs WARN and continues — trajectory alerting is advisory, never blocking.

### Retention

**AE trajectory retention** — `ClinicalCaseOutcomeObserver.handleAeCase()` extended:

After existing `clinical-ae` store (unchanged), adds second store:
1. `AeTrajectoryBuilder.buildTrajectory(ae, tenantId)` → full trajectory
2. Combine scalar pre-filter features + `"aeTrajectory"` → `FeatureValue.structList(trajectory)`
3. `cbrService.storeIdempotent(...)` into `clinical-ae-trajectory` domain

Same `PlanCbrCase` record type. Plan traces included.

**Site enrollment retention** — `TrialCompletionSiteTrajectoryWriter`:

Observes `TrialStatusChangedEvent(UUID trialId, TrialStatus oldStatus, TrialStatus newStatus, String tenantId)` — a new CDI event record added to `clinical-api`. The event is fired by the service method that transitions trial status (wherever `trial.status` is set to COMPLETED or TERMINATED). On COMPLETED/TERMINATED, iterates all sites in the trial, builds each site's enrollment trajectory, and stores as a CBR case in `clinical-site-enrollment` domain.

### REST API

**`GET /trials/{trialId}/adverse-events/{aeId}/trajectory`**
- Current trajectory data (partial or complete) + trend summary
- Response: `AeTrajectoryResponse(aeId, List<TrajectoryObservation>, TrendSummary)`
- `TrajectoryObservation(long secondsSinceReport, String escalationStatus, String susarStatus, String regulatoryStatus)`
- `TrendSummary(Map<String, DimensionTrend> dimensions)` where `DimensionTrend(double slope, double acceleration, int changePoints)` — keyed by dimension name (`escalation`, `susar`, `regulatory`)

**`GET /trials/{trialId}/adverse-events/{aeId}/trajectory/matches`**
- CBR trajectory matches for this AE's developing trajectory
- Response: `AeTrajectoryMatchResponse(List<TrajectoryMatch>, String traceId, String explanation)`
- `TrajectoryMatch(String caseId, double score, String outcome, List<TrajectoryObservation> trajectory, List<PlanStepResponse> steps)`
- Query params: `limit` (default 5, max 20), `minScore` (default 0.4)

**`GET /trials/{trialId}/sites/{siteId}/enrollment-trajectory`**
- Enrollment trajectory + trend summary + matched historical patterns
- Response: `SiteEnrollmentTrajectoryResponse(List<EnrollmentObservation>, TrendSummary, List<EnrollmentMatch>)`

All endpoints: `@RolesAllowed` for all 4 trial roles.

## Testing

### Unit Tests

- `AeTrajectoryBuilderTest` — trajectory from mock PlanItemStore; ordering; missing engine case; partial trajectory; relative timestamps
- `SiteEnrollmentTrajectoryBuilderTest` — weekly counts; empty site; cumulative vs period
- `AeTrajectoryAlertServiceTest` — no matches → empty; below threshold → empty; above threshold → event fired; weighted majority vote aggregation; Grade 1-2 AE with no engine case → single-observation trajectory evaluated; graceful failure
- `SiteEnrollmentAlertServiceTest` — same pattern
- `AeTrajectorySchemaTest` — schema has TimeSeries field with DtwSpec and TrendSpec

### Integration Tests (`@QuarkusTest`)

- `AeTrajectoryRoundTripTest` — store completed trajectory, retrieve with partial query, verify DTW scores (similar trajectory scores higher)
- `AeTrajectoryAlertIntegrationTest` — stored high-severity trajectories + new AE → verify alert event fires with correct predicted outcome
- `SiteEnrollmentTrajectoryRoundTripTest` — stalled vs healthy patterns, verify DTW matching
- `TrajectoryLifecycleHookTest` — `@InjectMock` on alert service, verify hooks in existing services call evaluate()

### Edge Cases

- AE with no engine case (Grade 1/2) → single-observation trajectory; evaluation still runs, matching initial profile against historical trajectory starts
- AE with engine case but no completed plan items → partial trajectory with initial report only
- Site with zero enrollments → empty trajectory, evaluation skipped
- Empty CBR store → no matches, no exception
- Concurrent evaluations → idempotent (read-only)

## Platform Primitives Used (Not Implemented Here)

All from `casehub-neocortex-memory-api`:

- `FeatureField.TimeSeries` — compound field with inner fields + timestamp + DtwSpec + TrendSpec
- `SimilaritySpec.DtwSpec(WarpingConstraint)` — Dynamic Time Warping similarity
- `TrendAnalyzer` — SLOPE, DELTA, VOLATILITY, ACCELERATION, CHANGE_POINTS
- `TrendEnrichmentCbrCaseMemoryStore` (Decorator Priority 90) — auto-enriches on store/retrieve
- `CbrSimilarityScorer` — per-field similarity dispatch including DTW

## Not In Scope

- Alert event consumers (notification wiring, blackboard flags) — #133
- DemoDataSeeder trajectory data — #134
- Grade regrading (AE grade changes over time) — #135; domain model doesn't support this yet; current trajectories track status progression at a fixed grade. When implemented, `grade` should be added back as a TimeSeries inner field with TrendAnalyzer enrichment
- Site enrollment scheduled periodic storage — #136; trial completion storage is sufficient for showcase
