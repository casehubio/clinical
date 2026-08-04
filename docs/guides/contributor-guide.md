# casehub-clinical — Contributor Guide

> Internals, architecture, and extension points for platform developers working on casehub-clinical.

**GitHub:** [casehubio/clinical](https://github.com/casehubio/clinical)

---

## Module Structure

```
casehub-clinical/
  api/          — public API: enums, constants, CDI events, SPI interfaces (no Panache entities)
  runtime/      — application logic: entities, services, REST resources, CDI wiring, CBR, memory
    src/main/java/io/casehub/clinical/
      casedefinition/  — CaseDefinition equivalence tests for YAML validation
      cbr/             — case-based reasoning: feature extraction, trajectory, alert, retrieval
      config/          — WorkCoreStrategyRegistrar
      demo/            — DemoDataSeeder, DemoActionResource, DemoCurrentPrincipal, DevSchemaInitializer
      entity/          — Panache Active Record entities (10 entities)
      ledger/          — LedgerEntry subclasses (16 subclasses, io.casehub.clinical.ledger package)
      memory/          — structured memory: domains, context records, ClinicalMemoryService
      resource/        — REST resources (9 resources)
      routing/         — ClinicalActionRiskClassifier, ClinicalRoutingFeatureExtractor, ClinicalTrustRoutingPolicyProvider
      service/         — core services, listeners, ledger writers, SPI defaults (50+ classes)
    src/main/resources/clinical/   — 7 YAML case definitions
    src/main/resources/db/migration/   — Flyway migrations (default/ and qhorus/)
    src/main/webui/    — Lit-based TypeScript frontend (Quinoa/esbuild, casehub-pages + blocks-ui)
```

**Active Record exception:** clinical has no downstream JPA consumers. Panache entities live in `runtime/` only; `api/` holds enums, constants, CDI events, and SPI interfaces only.

## Internal Architecture

### Multi-Site Coordination Structure

```
Trial case (ClinicalTrialCaseHub, trial-coordination.yaml)
  +-- CaseInstance per trial (engine blackboard)
  +-- site-level domain entities (NOT sub-cases — sites are long-running, not bounded)
  +-- runtime.signal() for Grade 4+ AE cross-site tracking
  +-- contextChange.filter binding for DSMB rollup
      -> fires when >= 2 sites have active Grade 4+ AEs simultaneously

Per-AE cases:
  +-- AeEscalationCaseHub (ae-escalation.yaml) — Grade 3+ only
  +-- ClinicalSusarOversightCaseHub (susar-oversight.yaml) — Grade 4/5 + unexpected + suspected
  +-- ClinicalRegulatorySubmissionCaseHub (regulatory-submission.yaml) — IND reporting

Per-deviation cases:
  +-- ClinicalDeviationCaseHub (deviation-review.yaml) — CRITICAL + PI approved -> IRB gate

Per-screening cases:
  +-- EligibilityScreeningCaseHub (eligibility-screening.yaml) — MARGINAL criteria -> IRB consultation

Per-amendment cases:
  +-- ProtocolAmendmentCaseHub (protocol-amendment.yaml) — advisory-only advisor worker
```

**Design decision:** No site sub-cases. Sites are long-running domain entities, not bounded work units that start and complete. Trial-level coordination uses a dedicated `CaseInstance` with a `contextChange.filter` binding — not parent-child case hierarchies.

### Two-Datasource Architecture

- **Default datasource** — clinical domain entities (`io.casehub.clinical.entity`) + casehub-work entities
- **`qhorus` named datasource** — qhorus entities + casehub-ledger entities + clinical ledger subclasses (`io.casehub.clinical.ledger`)

LedgerEntry subclasses must live in `io.casehub.clinical.ledger`, NOT in `io.casehub.clinical.entity` — Panache entities cannot span two persistence units. Hibernate ORM packages config:

```properties
quarkus.hibernate-orm.packages=io.casehub.clinical.entity
quarkus.hibernate-orm.qhorus.packages=io.casehub.ledger.runtime.entity,io.casehub.clinical.ledger
```

### Flyway Migration Structure

Clinical uses datasource-scoped subdirectories to avoid migration version collisions:

- `db/migration/default/` — clinical domain migrations (V100-V128)
- `db/migration/qhorus/` — clinical ledger subclass join tables (V2000-V2031)

Tests use `drop-and-create` + Flyway disabled.

**Convention:** V100-V999 for domain (default datasource); V2000+ for ledger (qhorus datasource). casehub-work ships V1-V21+; casehub-qhorus ships V1-V9+ — both scanned by Quarkus from transitive JARs.

### Entities (10 total)

| Entity | Package | Datasource | Key fields |
|--------|---------|------------|------------|
| `ClinicalTrial` | entity | default | protocolId, phase, sponsor, targetEnrollment, status, engineCaseId |
| `TrialSite` | entity | default | trialId, investigatorId, targetEnrollment, status |
| `PatientEnrollment` | entity | default | siteId, patientId, consentStatus, enrollmentStatus, screeningResult, treatmentArm |
| `AdverseEvent` | entity | default | enrollmentId, grade, eventType, slaDeadline, workItemId, escalationStatus, unexpected, suspected, susarOversightStatus |
| `ProtocolDeviation` | entity | default | siteId, deviationType, severity, piApprovalStatus, commandedAt, responseDeadline, escalationRequirement |
| `IrbApproval` | entity | default | deviationId, reviewType, committeeId, decision, deviationType |
| `ProtocolAmendment` | entity | default | trialId, proposedChange, status, amendmentCaseStatus, supervisorRecommendation |
| `SponsorNotification` | entity | default | trialId, deviationId, connectorId, destination, status, attempts |
| `AeGradeChange` | entity | default | adverseEventId, previousGrade, newGrade, changedAt, changedBy, reason |
| `TrialSafetySignal` | entity | default | trialId, signalType, affectedSiteCount, firstDetectedAt, resolvedAt |

### LedgerEntry Subclasses (16 total)

All in `io.casehub.clinical.ledger` package, qhorus datasource:

| Subclass | Discriminator | Migration | Written by |
|----------|--------------|-----------|------------|
| `AdverseEventLedgerEntry` | AE | V2000 | `AdverseEventLedgerWriter` |
| `ProtocolDeviationLedgerEntry` | ProtocolDeviation | V2001/V2002/V2009 | `DeviationLedgerWriter` |
| `IrbApprovalLedgerEntry` | IrbApproval | V2004 | `IrbApprovalLedgerWriter` |
| `AeEscalationLedgerEntry` | AeEscalation | V2005 | `AeEscalationLedgerWriter` |
| `SafetyOfficerNotificationLedgerEntry` | SafetyOfficerNotification | V2006 | `SafetyOfficerNotificationLedgerWriter` |
| `SponsorNotificationLedgerEntry` | SponsorNotification | V2020 | `SponsorNotificationLedgerWriter` |
| `SusarDecisionLedgerEntry` | SusarDecision | V2021 | `SusarDecisionLedgerWriter` |
| `ConsentWithdrawalLedgerEntry` | ConsentWithdrawal | V2022/V2028 | `ConsentWithdrawalService` |
| `RegulatorySubmissionLedgerEntry` | RegulatorySubmission | V2023 | `RegulatorySubmissionLedgerWriter` |
| `EligibilityScreeningLedgerEntry` | EligibilityScreening | V2024 | `EligibilityScreeningLedgerWriter` |
| `ProtocolAmendmentLedgerEntry` | ProtocolAmendment | V2025 | `ProtocolAmendmentLedgerWriter` |
| `IndReportFiledLedgerEntry` | IndReportFiled | V2026 | `RegulatorySubmissionLedgerWriter` |
| `IndReportBreachLedgerEntry` | IndReportBreach | V2027 | `RegulatorySubmissionLedgerWriter` |
| `CbrRetrievalLedgerEntry` | CbrRetrieval | V2029 | `CbrRetrievalLedgerWriter` |
| `AeGradeChangeLedgerEntry` | AeGradeChange | V2030 | `AeGradeChangeLedgerWriter` |
| `DsmbSafetySignalLedgerEntry` | DsmbSafetySignal | V2031 | `DsmbSafetySignalLedgerWriter` |

**Pattern:** Each ledger writer is an `@ApplicationScoped` bean that owns `sequenceNumber` computation via `findLatestBySubjectId`. See ADR-0002 in `docs/adr/`.

### Key SPIs (in `api/spi/`)

| SPI | Purpose | Default |
|-----|---------|---------|
| `AdverseEventEscalationPolicy` | CTCAE-based escalation routing | `DefaultAdverseEventEscalationPolicy` — Grade 3: senior monitor; Grade 4+: senior monitor + DSMB |
| `DeviationResponsePolicy` | PI response deadline + downstream action per severity | `DefaultDeviationResponsePolicy` — MINOR: 7d/NONE; MAJOR: 72h/SPONSOR_NOTIFICATION; CRITICAL: 24h/IRB_REVIEW |
| `IrbCommitteeAssignmentPolicy` | Maps deviation context to IRB committee assignment | `DefaultIrbCommitteeAssignmentPolicy @DefaultBean` |
| `PiIdentityResolver` | Resolves PI identity for deviation oversight | `DefaultPiIdentityResolver @DefaultBean` |
| `ProtocolAmendmentAdvisor` | Protocol amendment analysis (LLM supervisor slot) | `DefaultProtocolAmendmentAdvisor @DefaultBean` (always PROCEED); displaced by `LlmProtocolAmendmentAdvisor @ApplicationScoped` when `AgentProvider` available |
| `SponsorNotifier` (`api/`) | Protocol deviation sponsor notification delivery | `DurableSponsorNotifier` — persists + async retry |
| `SafetyOfficerNotifier` (`api/`) | Grade 3+ adverse event notification | `DefaultSafetyOfficerNotifier` — dispatches via casehub-connectors-core |
| `ClinicalPlanAdapter` (`cbr/`) | CBR plan reuse — adapts clinical plan steps | Implements blocks `PlanAdapter` SPI |

### Notification SPI Pattern

SPI interface in `api/`, implementation in `runtime/service/`, connector delivery via `casehub-connectors-core`. `DurableSponsorNotifier` persists a `SponsorNotification` entity in PENDING state and returns immediately; async delivery via `SponsorNotificationRetryJob` (poll-based). `SponsorNotificationRetryPolicy` (implements `Preference`) controls `maxAttempts`, `retryInterval`, optional `backoffMultiplier`, and optional `maxInterval`.

### Trust Routing

`ClinicalTrustRoutingPolicyProvider @ApplicationScoped` displaces `DefaultTrustRoutingPolicyProvider @DefaultBean`. Per-capability policies:

| Capability | Threshold | Min Observations | Quality Floor |
|-----------|-----------|-----------------|---------------|
| `safety-monitoring` | 0.75 | 20 min | 0.70 (safety-accuracy) |
| `eligibility-screening` | 0.70 | 15 min | — |
| `protocol-review` | 0.65 | 25 min | — |
| All others | `TrustRoutingPolicy.DEFAULT` | — | — |

`SusarAgentAttestationWriter` writes `LedgerAttestation` anchored to `WorkerDecisionEntry`; `TrustScoreJob` (24h schedule, gated by config) ingests attestations into Bayesian Beta scores. `attestorType`: HUMAN when named actor approved/rejected, SYSTEM on expiry.

### SUSAR Oversight

Dedicated `ClinicalSusarOversightCaseHub` + `susar-oversight.yaml` with capability binding via `spec.capabilities` + programmatic `.function()` registration. Three-phase `SusarOversightCaseService` with idempotency guard. Gate discrimination uses `AdverseEvent.findBySusarOversightCaseId` to avoid `CaseInstanceCache` race condition. `SusarCriteriaEvaluator @DefaultBean` implements `SusarEvaluatorFunction` — evaluates: grade in {GRADE_4, GRADE_5} AND unexpected AND suspected.

### ActionRiskClassifier

`ClinicalActionRiskClassifier @RiskClassifier @ApplicationScoped` discovered automatically by engine's `ChainedReactiveActionRiskClassifier`. Five regulatory gate constants in `ClinicalActionType`:
- `SUSAR_CRITERIA_DECISION` — ALWAYS-gated
- `SUSAR_REGULATORY_FILING` — ALWAYS-gated
- `PATIENT_WITHDRAWAL` — ALWAYS-gated
- `DOSE_MODIFICATION` — ALWAYS-gated
- `PROTOCOL_DEVIATION_RECORDING` — ALWAYS-gated

### IND Deadline Enforcement

`regulatory-submission.yaml` uses `expiresAtExpression: ".indReportingDeadline"` — `WorkItem.expiresAt` set to exact absolute FDA deadline from WORKING panel. `ClinicalIndReportingBreachPolicy` is a stateless two-tier `SlaBreachPolicy`: EscalateTo regulatory-leadership at 48h. `RegulatorySubmissionCompletedListener` / `RegulatorySubmissionBreachListener` handle lifecycle transitions. Both guards on `!= PENDING` for symmetry.

### AE Grade Regrading

`AdverseEventService.regradeAdverseEvent()` records `AeGradeChange` entity and fires `AeGradeChangedEvent`. Six listeners react independently:
- `AeGradeChangeEscalationListener` — re-evaluates escalation policy for the new grade
- `AeGradeChangeSusarListener` — re-evaluates SUSAR criteria (may start new oversight case)
- `AeGradeChangeRegulatoryListener` — re-evaluates IND reporting obligation
- `AeGradeChangeSafetyOfficerListener` — notifies safety officer if grade crosses threshold
- `AeGradeChangeTrajectoryListener` — updates trajectory tracking
- `AeGradeChangeLedgerWriter` — writes `AeGradeChangeLedgerEntry`

### LLM Protocol Amendment Advisor

`LlmProtocolAmendmentAdvisor @ApplicationScoped` displaces `DefaultProtocolAmendmentAdvisor @DefaultBean`. Invokes `AgentProvider` with a GCP/FDA/DSMB system prompt. Input: proposed change + trial blackboard snapshot (phase, status, AE counts, Grade 3+ count, Grade 5 present, prior amendments). Output: `AmendmentRecommendation` (PROCEED, REFER_TO_DSMB, HALT). Falls back to PROCEED on empty response or invocation failure.

### Multi-Tenancy

`tenant_id NOT NULL DEFAULT 'default'` on all domain entities. REST resources inject `CurrentPrincipal` and stamp `tenantId` at persist time. CDI events carry `String tenantId`. Entity finder methods use `findByIdForTenant(id, principal)`. Query isolation deferred to clinical#71.

## CBR Integration

### ClinicalCbrService

Central facade for CBR operations. Provides `retrieveWithAudit()` — retrieves precedents and writes `CbrRetrievalLedgerEntry` to the audit trail. Provides `storeIdempotent()` for case storage with scope.

### ClinicalCbrSchemaInitializer

`@Startup @ApplicationScoped` — registers CBR feature schemas for all six domains at application startup. Schemas define feature fields (Categorical, Numerical, CategoricalList, TimeSeries) and their similarity weights.

### ClinicalRoutingFeatureExtractor

`@ApplicationScoped` implementation of blocks `RoutingFeatureExtractor` — displaces `TextOnlyFeatureExtractor @DefaultBean`. Extracts structured features from `AgentRoutingContext`: CTCAE grade, AE type, patient demographics, trial phase, site location.

### AeCbrFeatureBuilder

Static utility — builds CBR features from `AeCbrContext` record. 14 features: grade (numeric 1-5), eventType (categoricalList), trialPhase, unexpected, suspected, treatmentArm, priorAeCount, safetyReviewOutcome, dsmbEscalated, indReportFiled, susarOversight, siteEnrollmentCount, siteTargetEnrollment, agentTrustScore. `buildQueryFeatures()` excludes outcome-only features (agentTrustScore). Schema also registers `mergeCount` for compaction (15 fields total).

### ClinicalCaseOutcomeObserver

Implements engine `CaseOutcomeObserver` SPI — stores `PlanCbrCase` for adverse events on case completion. Captures the full plan trace (binding name, capability, worker, outcome per step).

### AeEscalationPlanRetriever

Retrieves learned escalation plans from CBR for a given AE. Returns `EscalationPlanRecommendation` with recommended steps and confidence from precedent match quality. Exposed via `GET /api/adverse-events/{aeId}/escalation-plans`.

### Trajectory Tracking

- `AeTrajectoryBuilder` — generates multi-dimensional AE trajectory observations (escalation, susar, regulatory dimensions over time)
- `SiteEnrollmentTrajectoryBuilder` — generates site enrollment rate trajectories (periodCount, cumulativeCount per week)
- `AeTrajectoryAlertService` — detects AE trajectory deviation from expected patterns
- `SiteEnrollmentAlertService` — detects site enrollment rate deviation; fires `SiteEnrollmentAlertEvent`
- `SiteEnrollmentTrajectoryJob` — scheduled periodic trajectory storage
- `TrialCompletionSiteTrajectoryWriter` — records site-level trajectories for trial completion CBR
- `TrajectoryAlertListener` — consumes trajectory alert events and writes blackboard flags

### ClinicalScopeResolver

Resolves `Path` scope for CBR queries based on entity relationships. AEs scope to trial; deviations scope to trial via site. Enables multi-scope memory (trial-scoped, cross-trial) for DSMB pattern detection.

### ClinicalAgentTrustProvider

Provides trust score context for CBR feature extraction. Looks up per-agent trust scores from `ActorTrustScoreRepository`.

### Trial Safety Aggregation (CBR Phase 7)

`TrialSafetyAggregationJob` runs on 24h schedule. For each ACTIVE trial:
1. Collects AE data per site within configurable aggregation period
2. Detects two signal types:
   - **GRADE_THRESHOLD** — high-grade AE rate exceeds threshold across N+ sites
   - **CROSS_SITE_CLUSTER** — same event type reported at N+ sites
3. Stores `PlanCbrCase` in `TRIAL_SAFETY` CBR domain
4. Upserts `TrialSafetySignal` entity
5. Fires `DsmbSafetySignalEvent`
6. Auto-resolves stale signals no longer detected

Configurable via MicroProfile Config:
- `casehub.clinical.trial-safety.grade-threshold.min-grade` (default: 3)
- `casehub.clinical.trial-safety.grade-threshold.min-sites` (default: 3)
- `casehub.clinical.trial-safety.grade-threshold.min-rate` (default: 0.1)
- `casehub.clinical.trial-safety.cross-site-cluster.min-sites` (default: 3)
- `casehub.clinical.trial-safety.aggregation-period-days` (default: 90)

### CbrRetentionPurgeJob

Scheduled job for CBR case retention — purges old cases per configurable retention policy.

### CbrCompactionJob

Scheduled job — merges similar `clinical-ae` CBR cases into weighted representatives. Groups by exact categorical merge key (grade, eventType, trialPhase, unexpected, suspected). Weighted-average numerics by `mergeCount`, majority-vote categoricals. Disabled by default (`casehub.clinical.cbr.compaction.enabled=false`). Runs after retention purge.

### Precedent Storage

`ClinicalCaseOutcomeObserver` implements engine `CaseOutcomeObserver` SPI — stores `PlanCbrCase` for adverse events. `DeviationResolutionCbrWriter` and `AmendmentResolutionCbrWriter` use CDI event observers to store deviation and amendment outcomes. `AmendmentSupersessionObserver` handles amendment supersession. Structured features enable hybrid similarity (feature vector + semantic text).

## Memory Integration

### ClinicalMemoryDomains

Four structured memory domains:
- `PATIENT` (`clinical-patient`) — per-patient context (demographics, treatment arm, AE history)
- `SITE` (`clinical-site`) — per-site context (enrollment stats, investigator, trial membership)
- `DRUG` (`clinical-drug`) — cross-site AE signal aggregation per trial; entity convention: `trial:{trialId}`
- `IRB` (`clinical-irb`) — IRB decision precedent per deviation type; entity convention: `deviation-type:{deviationType}`

### ClinicalMemoryService

Coordinates memory writes across domain events. Stores structured context records:
- `ClinicalPatientContext` — patient demographics, treatment arm, enrollment status
- `ClinicalSiteContext` — site enrollment count, investigator, active AE count
- `ClinicalDrugContext` — per-trial AE signal aggregation across sites
- `ClinicalIrbContext` — IRB decision history per deviation type

## Engine Integration Notes

These apply to any consumer adding casehub-engine. Documented from clinical Layer 5.

- **CDI wiring:** `casehub-platform` and `casehub-platform-expression` must be on the runtime classpath when casehub-engine is present
- **Quartz incompatibility:** casehub-work scheduler beans use 5-field Unix cron; Quartz requires 6-7 field — exclude work scheduler beans via `quarkus.arc.exclude-types`
- **YAML binding:** `inputMapping` is correct (not `inputSchema`); `on.contextChange.filter` is correct (not `when`) — silent failures if wrong
- **Three-phase activation:** Services calling `startCase().join()` must NOT be `@Transactional` at that call site — split into three separate transactional calls to avoid Agroal pool deadlock (see `TrialActivationService`)
- **WorkloadProvider stub:** `StubWorkloadProvider @DefaultBean` required in test contexts that activate casehub-engine
- **`outputSchema(".")` mandatory:** Without it on `Capability.builder()`, Java-function worker output is silently discarded and goal conditions never fire
- **`private @Transactional` rejected:** CDI interceptors cannot proxy private methods — use package-private

## Demo Infrastructure

`runtime/src/main/java/io/casehub/clinical/demo/`:
- `DemoDataSeeder` — seeds a 3-site oncology trial scenario with patients, AEs, deviations, amendments, and Merkle verification
- `DemoActionResource` — PI approval + SUSAR gate approval demo endpoints
- `DemoCurrentPrincipal` — dev-mode tenant context (bypasses OIDC)
- `DevSchemaInitializer` — dev-mode schema setup

## Web UI Architecture

TypeScript frontend at `runtime/src/main/webui/`. Uses casehub-pages DSL for page composition and casehub-blocks-ui for components.

**Views** (`src/views/`):
- `work-queue.ts` — compliance officer work queue
- `safety-workbench.ts` — AE management dashboard
- `protocol-workbench.ts` — deviation and amendment management
- `operations.ts` — operational KPI dashboard

**Components** (`src/components/`):
- `cbr-precedents-panel.ts` — CBR precedent results display
- `commitment-lifecycle.ts` — PI commitment lifecycle visualization
- `gdpr-erasure-action.ts` — GDPR erasure action with confirmation dialog
- `regulatory-compliance-summary.ts` — regulatory compliance status summary
- `sla-breach-policy-indicator.ts` — SLA breach policy display
- `trust-feedback-display.ts` — trust score feedback

**Test suite:** Vitest tests in `src/__tests__/` covering all views and components.

**Build:** Quinoa (quarkus-quinoa) with esbuild. NPM packages resolved from Maven artifacts via `casehub-pages-npm` and `casehub-blocks-ui-npm` (unpacked to `.casehub-packages/` at Maven initialize phase).

## Key Epics

| # | Epic | Status |
|---|------|--------|
| 1 | Project scaffold | complete |
| 2 | Domain model — clinical trial entities and capability tags | complete |
| 3 | Multi-site sub-case structure | complete |
| 4 | Adverse event escalation — 24h and 7d GCP SLAs | complete |
| 5 | PI authorisation — formal commitment for protocol deviations | complete |
| 6 | IRB/ethics committee gate + AE escalation policy SPI | complete |
| 7 | GDPR and regulatory compliance — patient data | complete |
| 8 | Trust-weighted safety agent routing | complete |
| 9 | LLM supervisor mode — protocol amendment analysis | complete |
| 10 | 3-site showcase and ClinicalAgent comparison | complete |
| 83 | IND deadline enforcement (Layer 10) | complete |
| 88 | RBAC — casehub-platform-oidc wiring | complete |
| 86 | LLM-backed ProtocolAmendmentAdvisor via AgentProvider | complete |
| 93 | Clinical trial demo UI — guided walkthrough + explore dashboard | complete |
| 115 | Epic: CBR roadmap — phased clinical case-based reasoning | complete |
| 116 | CBR Phase 1 — CbrCaseMemoryStore for AE + deviation precedents | complete |
| 117 | CBR Phase 4 — outcome recording + retrieval audit trail | complete |
| 118 | CBR Phase 5 — learned escalation plans | complete |
| 119 | CBR Phase 6 — AE trajectory monitoring with DTW alerting | complete |
| 120 | CBR Phase 7 — multi-scope memory for DSMB pattern detection | complete |
| 135 | AE grade regrading — support grade changes over time | complete |

Issues: https://github.com/casehubio/clinical/issues

## Current State

**Status:** Active — Layers 1-10 complete. CBR Phases 1-7 complete. LLM advisor wired. Demo UI complete.

All tutorial layers are production-grade. The harness demonstrates that GCP, FDA, and EMA requirements are structurally satisfied by CaseHub's foundation where workflow-based LLM coordination cannot provide equivalent compliance guarantees.

Comparison baseline: ClinicalAgent (arXiv 2404.14777, ACM BCB '24). See `docs/comparison/clinicalagent.md` for the 10-row GCP/FDA gap table.

### Open Issues

| # | Title | Status |
|---|-------|--------|
| 142 | fix: sync with platform/engine API changes | open |
| 144 | feat: CBR case compaction — merge similar cases | open |
| 145 | feat: AE regrade capability — change CTCAE grade after initial assessment | open |
| 146 | feat: DSMB WorkItem for batch-detected safety signals | open |

## Design Documents

| Document | Location | Purpose |
|----------|----------|---------|
| ARC42STORIES.MD | `ARC42STORIES.MD` | Primary architecture record |
| LAYER-LOG.md | `LAYER-LOG.md` | Source-of-truth layer completion log |
| Use case analysis | `../parent/docs/use-case-analysis.md` | Use case scoring, clinical trial selection rationale (section 8.1) |
| ClinicalAgent comparison | `docs/comparison/clinicalagent.md` | 10-row GCP/FDA compliance gap table |
| DESIGN.md | `docs/DESIGN.md` | Design specification |
| ADRs | `docs/adr/` | Architecture decision records (9 ADRs) |
| Specs | `docs/specs/` | Design specifications (40+ specs) |
