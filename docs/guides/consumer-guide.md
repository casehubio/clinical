# casehub-clinical — Consumer Guide

> Clinical trial coordination harness built on CaseHub — eligibility screening, safety monitoring, PI authorisation, IRB gates, and FDA-compliant audit trail.

**GitHub:** [casehubio/clinical](https://github.com/casehubio/clinical)
**Tier:** Application

---

## Purpose

casehub-clinical is an agentic harness for clinical trial coordination. It coordinates eligibility screening agents, safety monitoring agents, PI authorisation gates, and IRB approval gates across multiple trial sites — producing an FDA-compliant, GDPR-aware, independently verifiable audit trail.

GCP domain knowledge is a prerequisite for the target audience — Java developers in regulated healthcare (pharma, biotech, clinical research). The same developer who evaluates CaseHub for their trial coordination system follows the tutorial to build it. Scored 24/25 on market fit — highest of all evaluated use cases.

## Tutorial Layers

Each layer adds one foundation module and makes its value tangible relative to the previous layer. Code at every layer is production-grade.

| Layer | Adds | Gap it closes | Status |
|-------|------|---------------|--------|
| 1 | Naive Java — no CaseHub | Baseline: direct service calls, no SLA, no audit | complete |
| 2 | casehub-work | No formal SLA for adverse event review (GCP: serious AE within 24h) | complete |
| 3 | casehub-qhorus | No formal obligation when coordinating PI authorisation and safety agents | complete |
| 4 | casehub-ledger | No FDA tamper-evident audit trail; no GDPR Art.17 consent withdrawal | complete |
| 5 | casehub-engine | Fixed trial pipeline; no adaptive paths for grade-based escalation or IRB gates | complete |
| 6 | Trial-level blackboard aggregation | No cross-site pattern detection; no DSMB rollup for simultaneous Grade 4+ events | complete |
| 7 | Trust routing | No trust model; experienced safety agents not prioritised on complex CTCAE Grade 4+ events | complete |
| 8 | ActionRiskClassifier oversight gate | No risk classification gate; SUSAR criteria assessment not automated; GDPR consent withdrawal | complete |
| 9 | Showcase — eligibility screening, protocol amendment, ClinicalAgent comparison | No showcase of eligibility screening or protocol amendment; no peer-reviewed comparison | complete |
| 10 | IND deadline enforcement | No absolute FDA deadline enforcement on regulatory submission WorkItems | complete |

## What It Owns

### Domain Model

Eight Panache Active Record entities in `runtime/src/main/java/io/casehub/clinical/entity/`:

- `ClinicalTrial` — the trial: protocolId, phase, sponsor, targetEnrollment, status, engineCaseId, sponsor notification config
- `TrialSite` — one investigator site: trialId, investigatorId, targetEnrollment, status
- `PatientEnrollment` — per-patient: siteId, patientId, consentStatus, enrollmentStatus, screeningResult, treatmentArm, enrolledAt, withdrawnAt
- `AdverseEvent` — safety event: enrollmentId, grade, eventType, occurredAt, reportedAt, slaDeadline, workItemId, escalationStatus, unexpected, suspected, susarOversightStatus, susarOversightCaseId, regulatorySubmissionStatus, engineCaseId
- `ProtocolDeviation` — deviation: siteId, deviationType, severity, piApprovalStatus, piCommandChannelName, commandedAt, responseDeadline, escalationRequirement, engineCaseId
- `IrbApproval` — IRB/ethics gate: deviationId, reviewType, committeeId, decisionDeadline, decision, deviationType
- `ProtocolAmendment` — amendment: trialId, proposedChange, status, amendmentCaseStatus, supervisorRecommendation, proposedAt
- `SponsorNotification` — durable notification: trialId, deviationId, connectorId, destination, status, attempts, lastAttemptAt
- `AeGradeChange` — grade regrading history: adverseEventId, previousGrade, newGrade, changedAt, changedBy, reason
- `TrialSafetySignal` — cross-site safety signal: trialId, signalType, affectedSiteCount, summary, firstDetectedAt, lastDetectedAt, resolvedAt

All entities carry `tenantId NOT NULL DEFAULT 'default'` for multi-tenancy.

### Capability Tags

Defined in `ClinicalCapabilities` (`api/`):

`eligibility-screening`, `safety-monitoring`, `protocol-review`, `irb-consultation`, `pi-authorisation`, `data-safety-monitoring`, `regulatory-submission`, `trial-supervisor`

### Trust Dimensions

Defined in `ClinicalTrustDimensions` (`api/`):

- `safety-accuracy` — adverse event classification accuracy vs subsequent safety outcomes
- `eligibility-precision` — false positive rate on eligibility screening
- `protocol-adherence` — track record of flagging deviations vs missing them

### Key Services

- **Adverse event reporting** — `AdverseEventService` creates WorkItem with grade-keyed `claimDeadline` (Grade 3/4: 24h, Grade 5: 1h, Grade 1/2: 7d); writes `AdverseEventLedgerEntry`
- **AE grade regrading** — `AdverseEventService.regradeAdverseEvent()` records `AeGradeChange`, fires `AeGradeChangedEvent`; listeners re-evaluate escalation, SUSAR, regulatory, safety officer notification, and trajectory
- **Adverse event escalation** — `AeEscalationCaseService` starts `ae-escalation.yaml` engine case for Grade 3+; `AeEscalationListener` handles completion
- **PI authorisation** — `ProtocolDeviationService` creates per-deviation qhorus channel, sends COMMAND to named PI; `PiResponseListener` processes structured JSON responses; `DeviationResponsePolicy` SPI controls deadlines per severity (MINOR: 7d, MAJOR: 72h, CRITICAL: 24h); MAJOR deviations trigger GCP 4.5 sponsor notification via `SponsorNotifier` SPI
- **Sponsor notification** — `DurableSponsorNotifier` persists `SponsorNotification` entity in PENDING state; `SponsorNotificationRetryJob` handles async retry with configurable `SponsorNotificationRetryPolicy` (maxAttempts, retryInterval, backoffMultiplier, maxInterval); delivery via `casehub-connectors-core`
- **IRB/ethics committee gate** — `IrbDeviationCaseService` starts `deviation-review.yaml` engine case on CRITICAL deviation + PI approval; 72h WorkItem with four terminal outcomes (APPROVED/REJECTED/DEFERRED/EXPIRED); `IrbCommitteeAssignmentPolicy` SPI maps context to committee
- **SUSAR oversight** — `ClinicalSusarOversightCaseHub` + `susar-oversight.yaml`; `SusarCriteriaEvaluator` checks Grade 4/5 + unexpected + suspected; `SusarGateDecisionListener` handles gate outcomes; `SusarAgentAttestationWriter` writes attestations for trust scoring
- **Trust routing** — `ClinicalTrustRoutingPolicyProvider` with per-capability policies: SAFETY_MONITORING threshold=0.75 (20-min observations, 0.70 safety-accuracy quality floor), ELIGIBILITY_SCREENING threshold=0.70 (15 min), PROTOCOL_REVIEW threshold=0.65 (25 min)
- **Regulatory submission** — `RegulatorySubmissionCaseService` triggers on Grade 3+ unexpected AE; IND expedited safety reporting (Grade 3: 15-day, Grade 4/5: 7-day); `regulatory-submission.yaml` with `expiresAtExpression` for absolute FDA deadline
- **IND deadline enforcement** — `ClinicalIndReportingBreachPolicy` is a stateless two-tier `SlaBreachPolicy`: escalates to regulatory-leadership at 48h; `RegulatorySubmissionCompletedListener` / `RegulatorySubmissionBreachListener` handle lifecycle
- **Eligibility screening** — `EligibilityScreeningService` evaluates criteria; MARGINAL results trigger IRB consultation via `eligibility-screening.yaml` engine case (72h SLA); `EligibilityScreeningLedgerEntry` records decision
- **Protocol amendment** — `ProtocolAmendmentService` proposes amendments; `ProtocolAmendmentAdvisor` SPI provides LLM supervisor slot; `LlmProtocolAmendmentAdvisor` (displaces `DefaultProtocolAmendmentAdvisor @DefaultBean`) invokes `AgentProvider` with GCP/FDA/DSMB system prompt; recommendations: PROCEED, REFER_TO_DSMB, HALT
- **GDPR consent withdrawal** — `ConsentWithdrawalService` pseudonymises patientId, calls `LedgerErasureService.erase()` with `GDPR_ART_17_REQUEST`, erases patient memories; `GdprErasureService` provides patient-scoped erasure across all enrollments
- **Safety officer notification** — `SafetyOfficerNotificationListener` observes `AdverseEventReportedEvent` (Grade 3+ only, fires once per AE); `DefaultSafetyOfficerNotifier` dispatches via connectors; Grade 5 carries `[CRITICAL]` prefix
- **Trial-level safety aggregation** — `TrialSafetyAggregationJob` (24h scheduled) detects cross-site AE patterns (grade threshold, cross-site cluster); stores `TrialSafetySignal` entities; fires `DsmbSafetySignalEvent`; stores CBR cases in `TRIAL_SAFETY` domain
- **Trial activation** — `TrialActivationService` performs three-phase activation (commit status, startCase outside @Transactional, commit caseId) to avoid Agroal pool deadlock

### REST API

#### Trial Management

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `POST` | `/trials` | Register a trial | SPONSOR |
| `GET` | `/trials/{t}` | Get trial | all |
| `PATCH` | `/trials/{t}/sponsor-config` | Update sponsor notification config | SPONSOR |
| `POST` | `/trials/{t}/activate` | Activate trial (three-phase activation) | SPONSOR |

#### Site Management

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `POST` | `/trials/{t}/sites` | Add investigator site | SPONSOR |
| `GET` | `/trials/{t}/sites/{s}` | Get site | all |

#### Patient and Adverse Event Management

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `POST` | `/trials/{t}/sites/{s}/patients` | Enroll patient | INVESTIGATOR, COORDINATOR |
| `GET` | `/trials/{t}/sites/{s}/patients/{e}` | Get patient enrollment | all |
| `POST` | `/trials/{t}/sites/{s}/patients/{e}/screen` | Screen patient against eligibility criteria | INVESTIGATOR, COORDINATOR |
| `POST` | `/trials/{t}/sites/{s}/patients/{e}/adverse-events` | Report adverse event | INVESTIGATOR, COORDINATOR |
| `GET` | `/trials/{t}/sites/{s}/patients/{e}/adverse-events/{ae}` | Get adverse event | all |
| `POST` | `/trials/{t}/sites/{s}/patients/{e}/adverse-events/{ae}/regrade` | Regrade adverse event (CTCAE grade change) | INVESTIGATOR, COORDINATOR |
| `GET` | `/trials/{t}/sites/{s}/patients/{e}/adverse-events/{ae}/grade-history` | Get AE grade change history | INVESTIGATOR, COORDINATOR |
| `POST` | `/trials/{t}/sites/{s}/patients/{e}/withdraw-consent` | GDPR Art.17 consent withdrawal | INVESTIGATOR |

#### Protocol Deviations

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `POST` | `/trials/{t}/sites/{s}/deviations` | Report protocol deviation | INVESTIGATOR, COORDINATOR |
| `GET` | `/trials/{t}/sites/{s}/deviations/{d}` | Get deviation | all |

#### Protocol Amendments

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `POST` | `/trials/{t}/amendments` | Propose protocol amendment | SPONSOR, INVESTIGATOR |
| `GET` | `/trials/{t}/amendments/{id}` | Get amendment status | all |

#### Audit and Compliance

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `GET` | `/trials/{t}/sites/{s}/patients/{e}/ledger/verify` | Merkle chain verification (valid + merkleRoot) | all |
| `GET` | `/trials/{t}/sites/{s}/patients/{e}/audit/prov` | W3C PROV-DM export (application/ld+json) | all |
| `GET` | `/trials/{t}/sites/{s}/patients/{e}/audit/entries/{id}/proof` | Merkle inclusion proof | all |

#### GDPR Erasure

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `DELETE` | `/api/gdpr/erasure/patients/{patientId}` | Patient-scoped GDPR erasure across all enrollments | SPONSOR, COORDINATOR |

#### CBR Escalation Plans

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `GET` | `/api/adverse-events/{aeId}/escalation-plans` | Learned escalation plan recommendations from CBR | all |

#### Trial Dashboard (read-only aggregation endpoints)

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `GET` | `/trials/{t}/summary` | Trial summary (counts) | all |
| `GET` | `/trials/{t}/patients` | All patients across sites | all |
| `GET` | `/trials/{t}/adverse-events` | All AEs across sites (with SLA remaining, grade history) | all |
| `GET` | `/trials/{t}/deviations` | All deviations across sites (with IRB decision) | all |
| `GET` | `/trials/{t}/agents` | Agent trust scores and maturity per capability | all |
| `GET` | `/trials/{t}/adverse-events/{ae}/governance` | SUSAR oversight governance context | all |
| `GET` | `/trials/{t}/ledger-entries` | All ledger entries for trial (with optional type filter) | all |
| `GET` | `/trials/{t}/sites` | Sites with enrollment/AE/deviation counts | all |
| `GET` | `/trials/{t}/deviations/{d}/commitment` | Commitment lifecycle for a deviation | SPONSOR, INVESTIGATOR, COORDINATOR |

#### CBR Precedent Endpoints

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `GET` | `/trials/{t}/adverse-events/{ae}/precedents` | Similar past AEs — feature vector + semantic similarity | all |
| `GET` | `/trials/{t}/deviations/{d}/precedents` | Similar past protocol deviations | all |
| `GET` | `/trials/{t}/amendments/{id}/precedents` | Similar past protocol amendments | all |

#### Trajectory Endpoints

| Method | Endpoint | Purpose | Roles |
|--------|----------|---------|-------|
| `GET` | `/trials/{t}/adverse-events/{ae}/trajectory` | AE progression trajectory with trend analysis | all |
| `GET` | `/trials/{t}/adverse-events/{ae}/trajectory/matches` | AE trajectory precedent matches via CBR | all |
| `GET` | `/trials/{t}/sites/{s}/enrollment-trajectory` | Site enrollment trajectory with trend analysis | all |

### CBR Domains

Six case-based reasoning domains defined in `ClinicalCbrDomains`:

| Domain | Key | Purpose |
|--------|-----|---------|
| `AE` | `clinical-ae` | Adverse event safety escalation decisions |
| `DEVIATION` | `clinical-deviation` | Protocol deviation PI/IRB approval patterns |
| `AMENDMENT` | `clinical-amendment` | Protocol amendment advisor recommendations |
| `AE_TRAJECTORY` | `clinical-ae-trajectory` | AE progression trajectory patterns (DTW-based) |
| `SITE_ENROLLMENT` | `clinical-site-enrollment` | Site enrollment rate trajectories |
| `TRIAL_SAFETY` | `clinical-trial-safety` | Cross-site trial-level safety signals |

### Memory Domains

Four structured memory domains defined in `ClinicalMemoryDomains`:

| Domain | Key | Purpose |
|--------|-----|---------|
| `PATIENT` | `clinical-patient` | Per-patient context (demographics, treatment arm) |
| `SITE` | `clinical-site` | Per-site context (enrollment stats, investigator) |
| `DRUG` | `clinical-drug` | Cross-site AE signal aggregation per trial |
| `IRB` | `clinical-irb` | IRB decision precedent per deviation type |

### Web UI

Lit-based web UI built with casehub-blocks-ui components and casehub-pages. Served via Quinoa (esbuild). Source at `runtime/src/main/webui/`.

**Views** (`src/views/`):
- **Work Queue** (`work-queue.ts`) — compliance officer work queue with `work-item-inbox`
- **Safety Workbench** (`safety-workbench.ts`) — adverse event management with `approval-gate`, `sla-indicator`, `data-table`
- **Protocol Workbench** (`protocol-workbench.ts`) — protocol deviation and amendment management
- **Operations** (`operations.ts`) — operational dashboard with `kpi-metric-row` for trial metrics

**Components** (`src/components/`):
- `cbr-precedents-panel.ts` — CBR precedent display
- `commitment-lifecycle.ts` — PI commitment lifecycle display
- `gdpr-erasure-action.ts` — GDPR erasure action with confirmation dialog
- `regulatory-compliance-summary.ts` — regulatory compliance status
- `sla-breach-policy-indicator.ts` — SLA breach policy display
- `trust-feedback-display.ts` — trust score feedback display

### RBAC

Four roles defined in `ClinicalGroups` (`api/`): `SPONSOR`, `INVESTIGATOR`, `COORDINATOR`, `MONITOR`. All REST endpoints enforce `@RolesAllowed`. OIDC-based identity via `casehub-platform-oidc` (`OidcCurrentPrincipal`).

### CDI Events (in `api/`)

| Event | Fired when |
|-------|-----------|
| `AdverseEventReportedEvent` | AE reported |
| `AeEscalationCompletedEvent` | AE escalation case completes (includes siteId, unexpected flag) |
| `AeGradeChangedEvent` | AE CTCAE grade regraded |
| `AeTrajectoryAlertEvent` | AE trajectory deviation detected |
| `DsmbSafetySignalEvent` | Cross-site safety signal detected by aggregation job |
| `EligibilityScreeningEvent` | Patient eligibility screening completed |
| `IrbApprovalResolvedEvent` | IRB committee decision reached |
| `ProtocolAmendmentProposedEvent` | Protocol amendment proposed |
| `ProtocolAmendmentResolvedEvent` | Protocol amendment resolved |
| `ProtocolDeviationResolvedEvent` | Protocol deviation terminal state reached |
| `SiteEnrollmentAlertEvent` | Site enrollment trajectory deviation detected |
| `SponsorNotificationExhaustedEvent` | Sponsor notification retries exhausted |
| `TrialStatusChangedEvent` | Trial status changed |

### Case Definitions (YAML)

Seven engine case definitions in `runtime/src/main/resources/clinical/`:

| File | CaseHub class | Purpose |
|------|---------------|---------|
| `trial-coordination.yaml` | `ClinicalTrialCaseHub` | Trial-level case; DSMB rollup binding fires on Grade 4+ cross-site pattern |
| `ae-escalation.yaml` | `ClinicalAdverseEventCaseHub` | Grade 3+ AE escalation; safety-review + dsmb-escalation humanTasks |
| `deviation-review.yaml` | `ClinicalDeviationCaseHub` | IRB gate for CRITICAL deviation + PI approval; 72h WorkItem |
| `susar-oversight.yaml` | `ClinicalSusarOversightCaseHub` | SUSAR criteria evaluation; capability binding + programmatic function |
| `regulatory-submission.yaml` | `ClinicalRegulatorySubmissionCaseHub` | IND expedited reporting; humanTask with `expiresAtExpression` |
| `eligibility-screening.yaml` | `EligibilityScreeningCaseHub` | Marginal eligibility criteria; IRB consultation WorkItem (72h) |
| `protocol-amendment.yaml` | `ProtocolAmendmentCaseHub` | Advisory-only; ProtocolAmendmentAdvisor capability worker |

## Dependencies

```
casehub-clinical
  -> casehub-engine                       (IRB gate, AE escalation, CasePlanModel, stage gating)
  -> casehub-work-engine-adapter          (HumanTaskScheduleHandler + WorkItemLifecycleAdapter)
  -> casehub-engine-scheduler-quartz      (Quartz worker execution)
  -> casehub-engine-persistence-hibernate (JPA CaseInstance persistence)
  -> casehub-platform                     (runtime scope — @DefaultBean mocks for engine CDI wiring)
  -> casehub-platform-expression          (runtime scope — JQEvaluator for engine expression evaluation)
  -> casehub-platform-config              (YAML-backed SingleValuePreference for retry policy)
  -> casehub-platform-oidc                (RBAC: OidcCurrentPrincipal, @RolesAllowed enforcement)
  -> casehub-platform-agent-api           (AgentProvider SPI for LlmProtocolAmendmentAdvisor)
  -> casehub-ledger                       (FDA Merkle audit, GDPR erasure, EU AI Act Art.12, trust scoring)
  -> casehub-work                         (IRB/PI WorkItems with SLA and escalation)
  -> casehub-qhorus                       (COMMAND to PI, commitment lifecycle, safety agent channels)
  -> casehub-connectors-core              (sponsor and safety officer notification delivery)
  -> casehub-neocortex-memory-api         (memory domain abstractions)
  -> casehub-neocortex-memory-jpa         (prod — JPA CaseMemoryStore)
  -> casehub-neocortex-memory-inmem       (test scope — @Alternative CaseMemoryStore)
  -> casehub-neocortex-memory-cbr-inmem   (CBR — InMemoryCbrCaseMemoryStore for precedent retrieval)
  -> casehub-engine-ledger                (TrustWeightedAgentStrategy, WorkerDecisionEventCapture, TrustScoreCache)
  -> casehub-blocks                       (CBR: RoutingFeatureExtractor SPI — ClinicalRoutingFeatureExtractor)
  -> casehub-worker-api                   (WorkerResult, PlannedAction, Worker primitives)
  -> casehub-pages-npm                    (Web UI: page(), tree(), loadSite() — Quinoa frontend)
  -> casehub-blocks-ui-npm                (Web UI: data-table, approval-gate, sla-indicator, kpi-metric-row)
```

## The Compliance Gap It Closes

ClinicalAgent (peer-reviewed baseline, arXiv 2404.14777) structurally cannot provide:

- **Adverse event SLA enforcement** (GCP: serious events within 24h) — WorkItem `claimDeadline`
- **Protocol deviation authorisation by named PI** — COMMAND commitment lifecycle
- **Consent withdrawal** (GDPR Art.17) — ledger erasure and decision context sanitisation
- **Multi-site independence with trial-level rollup** — engine blackboard aggregation with `contextChange.filter`
- **FDA tamper-evident audit trail** — Merkle MMR + Ed25519-signed checkpoints
- **Trust-weighted safety agent routing** — Bayesian Beta from outcome attestations
- **Adaptive protocol paths** — IRB gate and grade-based AE escalation via CasePlanModel
- **IND deadline enforcement** — exact absolute FDA deadlines with two-tier breach escalation
- **Case-based reasoning** — structured precedent retrieval with audit trail for AE, deviation, amendment, and trajectory domains

## What It Does NOT Own

- Case orchestration engine, plan models, bindings — **casehub-engine**
- Work items, SLA tracking, escalation policies — **casehub-work**
- Messaging channels, COMMAND/RESPONSE lifecycle, commitments — **casehub-qhorus**
- Merkle audit trail, ledger erasure, compliance supplements — **casehub-ledger**
- Connector delivery (Slack, SMS, WhatsApp) — **casehub-connectors**
- Trust scoring infrastructure, Bayesian Beta computation — **casehub-engine-ledger**
- CBR memory store, similarity search, feature schemas — **casehub-neocortex-memory** (formerly casehub-blocks)
- Worker primitives (WorkerResult, PlannedAction) — **casehub-worker-api**
- LLM invocation primitive (AgentProvider SPI) — **casehub-platform-agent-api**
- Page rendering, site loading, dataset binding — **casehub-pages**
- UI component library (data-table, approval-gate, etc.) — **casehub-blocks-ui**
