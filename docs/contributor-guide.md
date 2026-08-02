# casehub-clinical — Contributor Guide

> Internals, architecture, and extension points for platform developers working on casehub-clinical.

**GitHub:** [casehubio/clinical](https://github.com/casehubio/clinical)

---

## Module Structure

```
casehub-clinical/
  api/          — public API: enums, constants, SPI interfaces (no Panache entities)
  runtime/      — application logic: entities, services, REST resources, CDI wiring
  webui/        — Lit-based frontend (Quinoa/esbuild, casehub-pages + blocks-ui)
```

**Active Record exception:** clinical has no downstream JPA consumers. Panache entities live in `runtime/` only; `api/` holds enums and constants only.

## Internal Architecture

### Multi-Site Sub-Case Structure

```
Trial case (parent)
+-- Site A sub-case
|   +-- Patient enrollment bindings
|   +-- Adverse event monitoring bindings
|   +-- Protocol deviation bindings
+-- Site B sub-case
|   +-- ...
+-- Trial-level rollup binding (aggregates site sub-cases)
    -> DSMB review triggers when safety signal threshold crossed across >= 2 sites
```

Trial-level binding fires on aggregated context from all site sub-cases — no site-level agent reasons about this; the engine detects the cross-site pattern from accumulated blackboard state.

### Two-Datasource Architecture

- **Default datasource** — clinical domain entities (`io.casehub.clinical.entity`) + casehub-work entities
- **`qhorus` named datasource** — qhorus entities + casehub-ledger entities + clinical ledger subclasses (`io.casehub.clinical.ledger`)

LedgerEntry subclasses must live in `io.casehub.clinical.ledger`, NOT in `io.casehub.clinical.entity` — Panache entities cannot span two persistence units.

### Flyway Migration Structure

Clinical uses datasource-scoped subdirectories to avoid migration version collisions:

- `db/migration/default/` — clinical domain migrations (V100-V999)
- `db/migration/qhorus/` — clinical ledger subclass join tables (V2000+)

Tests use `drop-and-create` + Flyway disabled.

### Key SPIs (in `api/spi/`)

| SPI | Purpose | Default |
|-----|---------|---------|
| `AdverseEventEscalationPolicy` | CTCAE-based escalation routing | `DefaultAdverseEventEscalationPolicy` — Grade 3: senior monitor; Grade 4+: senior monitor + DSMB |
| `IrbCommitteeAssignmentPolicy` | Maps deviation context to IRB committee assignment | `@DefaultBean` in `runtime/service/` |
| `SponsorNotifier` | Protocol deviation sponsor notification delivery | `DurableSponsorNotifier` — persists + async retry |
| `SafetyOfficerNotifier` | Grade 3+ adverse event notification | Dispatches via casehub-connectors-core |
| `ProtocolAmendmentAdvisor` | Protocol amendment analysis | `@DefaultBean` stub (always PROCEED); real LLM impl pending |
| `ClinicalPlanAdapter` | CBR plan reuse — adapts clinical plan steps | Adopts `PlanAdapter` SPI |

### Notification SPI Pattern

SPI interface in `api/spi/`, implementation in `runtime/service/`, connector delivery via `casehub-connectors-core`. `DurableSponsorNotifier` persists a `SponsorNotification` entity in PENDING state and returns immediately; async delivery via `SponsorNotificationRetryJob` (poll-based). `SponsorNotificationRetryPolicy` controls `maxAttempts`, `retryInterval`, optional `backoffMultiplier`, and optional `maxInterval`.

### Trust Routing

`ClinicalTrustRoutingPolicyProvider @ApplicationScoped` displaces `DefaultTrustRoutingPolicyProvider @DefaultBean`. SAFETY_MONITORING threshold=0.75, 20-min observations, 0.70 safety-accuracy quality floor. `SusarAgentAttestationWriter` writes `LedgerAttestation` anchored to `WorkerDecisionEntry`; TrustScoreJob ingests attestations into Bayesian Beta scores.

### SUSAR Oversight

Dedicated `ClinicalSusarOversightCaseHub` + `susar-oversight.yaml` with capability binding via `spec.capabilities` + programmatic `.function()` registration. Three-phase `SusarOversightCaseService` with idempotency guard. Gate discrimination uses `AdverseEvent.findBySusarOversightCaseId` to avoid `CaseInstanceCache` race condition.

### IND Deadline Enforcement

`regulatory-submission.yaml` uses `expiresAtExpression` — `WorkItem.expiresAt` set to exact absolute FDA deadline. `ClinicalIndReportingBreachPolicy` is a stateless two-tier `SlaBreachPolicy`: EscalateTo regulatory-leadership at 48h. `RegulatorySubmissionCompletedListener` / `RegulatorySubmissionBreachListener` handle lifecycle transitions.

### Multi-Tenancy

`tenant_id NOT NULL DEFAULT 'default'` on all 6 domain entities. REST resources and services inject `CurrentPrincipal` and stamp `tenantId` at persist time. CDI events carry `String tenantId`. Query isolation deferred to clinical#71.

## CBR Integration

### ClinicalRoutingFeatureExtractor

`@ApplicationScoped` implementation of blocks `RoutingFeatureExtractor` — displaces `TextOnlyFeatureExtractor @DefaultBean`. Extracts structured features from `AgentRoutingContext`: CTCAE grade, AE type, patient demographics, trial phase, site location.

### ClinicalCbrService

Central facade for CBR `CbrCaseMemoryStore` operations. Owns domain-specific feature extraction and precedent query logic.

### ClinicalCbrDomains

Domain constants: ADVERSE_EVENT, PROTOCOL_DEVIATION, PROTOCOL_AMENDMENT, CLINICAL_AE_TRAJECTORY, CLINICAL_SITE_ENROLLMENT.

### Trajectory Tracking

- `AeTrajectoryBuilder` / `SiteEnrollmentTrajectoryBuilder` — generate trajectory data
- `AeTrajectoryAlertService` / `SiteEnrollmentAlertService` — detect deviation from expected patterns
- `TrialCompletionSiteTrajectoryWriter` — records site-level trajectories for trial completion CBR

### Precedent Storage

`ClinicalCaseOutcomeObserver` implements engine `CaseOutcomeObserver` SPI — stores `PlanCbrCase` for adverse events. Deviation and amendment writers use CDI event observers. Structured features enable hybrid similarity (feature vector + semantic text).

## Engine Integration Notes

These apply to any consumer adding casehub-engine. Documented from clinical Layer 5.

- **CDI wiring:** `casehub-platform` and `casehub-platform-expression` must be on the runtime classpath when casehub-engine is present
- **Quartz incompatibility:** casehub-work scheduler beans use 5-field Unix cron; Quartz requires 6-7 field — exclude work scheduler beans via `quarkus.arc.exclude-types`
- **YAML binding:** `inputMapping` is correct (not `inputSchema`); `on.contextChange.filter` is correct (not `when`) — silent failures if wrong
- **Three-phase activation:** Services calling `startCase().join()` must NOT be `@Transactional` at that call site — split into three separate transactional calls to avoid Agroal pool deadlock
- **WorkloadProvider stub:** `StubWorkloadProvider @DefaultBean` required in test contexts that activate casehub-engine

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

Issues: https://github.com/casehubio/clinical/issues?label=epic

## Current State

**Status:** Active — Layers 1-10 complete.

All tutorial layers are production-grade. The harness demonstrates that GCP, FDA, and EMA requirements are structurally satisfied by CaseHub's foundation where workflow-based LLM coordination cannot provide equivalent compliance guarantees.

Comparison baseline: ClinicalAgent (arXiv 2404.14777, ACM BCB '24). See `docs/comparison/clinicalagent.md` for the 10-row GCP/FDA gap table.

## Design Documents

| Document | Location | Purpose |
|----------|----------|---------|
| ARC42STORIES.MD | `ARC42STORIES.MD` | Primary architecture record |
| LAYER-LOG.md | `LAYER-LOG.md` | Source-of-truth layer completion log |
| Use case analysis | `../parent/docs/use-case-analysis.md` | Use case scoring, clinical trial selection rationale (section 8.1) |
| Tutorial strategy | `docs/tutorial-strategy.md` | Teaching objectives per layer |
| ClinicalAgent comparison | `docs/comparison/clinicalagent.md` | 10-row GCP/FDA compliance gap table |
| DESIGN.md | `docs/DESIGN.md` | Design specification |
