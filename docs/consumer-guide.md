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

- `ClinicalTrial` — the trial: protocolId, phase, sponsor, sites, status
- `TrialSite` — one investigator site: siteId, investigatorId, patients, status
- `PatientEnrollment` — per-patient: patientId, eligibilityCriteria, consentStatus, safetyEvents
- `ProtocolDeviation` — recorded deviation: deviationType, severity, piApprovalStatus, reportedAt
- `AdverseEvent` — safety event: severity, reportedAt, slaDeadline, escalationStatus
- `IrbApproval` — IRB/ethics gate: reviewType, committeeId, decisionDeadline, decision

### Capability Tags

`eligibility-screening`, `safety-monitoring`, `protocol-review`, `irb-consultation`, `pi-authorisation`, `data-safety-monitoring`, `regulatory-submission`, `trial-supervisor`

### Trust Dimensions

- `safety-accuracy` — adverse event classification accuracy vs subsequent safety outcomes
- `eligibility-precision` — false positive rate on eligibility screening
- `protocol-adherence` — track record of flagging deviations vs missing them

### Key Services

- **Adverse event escalation** — 24h/7d GCP SLA WorkItems with CTCAE grading
- **PI authorisation** — formal COMMAND creates Commitment; deviation requires named PI approval; MAJOR deviations trigger GCP 4.5 sponsor notification via `SponsorNotifier` SPI
- **IRB/ethics committee gate** — CRITICAL protocol deviation + PI approval triggers 72h WorkItem with four terminal outcomes (APPROVED/REJECTED/DEFERRED/EXPIRED)
- **Trust routing** — `ClinicalTrustRoutingPolicyProvider` with SAFETY_MONITORING threshold=0.75, 20-min observations, 0.70 safety-accuracy quality floor
- **Regulatory submission** — Grade 3/4/5 + unexpected AE triggers IND expedited safety reporting case (Grade 3: 15-day; Grade 4/5: 7-day)
- **GDPR consent withdrawal** — `ConsentWithdrawalService` pseudonymises patientId, erases ledger entries and patient memories
- **IND deadline enforcement** — exact absolute FDA deadline via `expiresAtExpression`, two-tier breach escalation policy

### REST API

| Endpoint | Purpose |
|----------|---------|
| `POST /trials/{t}/activate` | Activate a trial (three-phase activation) |
| `POST /trials/{t}/escalation-plans` | Create adverse event escalation plan |
| `POST /trials/{t}/sites/{s}/patients/{e}/screen` | Screen patient against eligibility criteria |
| `POST /trials/{t}/amendments` | Propose protocol amendment |
| `GET /trials/{t}/amendments/{id}` | Get amendment status |
| `POST /{enrollmentId}/withdraw-consent` | GDPR Art.17 consent withdrawal |
| `GET /audit/prov` | W3C PROV-DM export |
| `GET /audit/entries/{id}/proof` | Merkle inclusion proof |

### REST Precedent Endpoints (CBR)

| Endpoint | Returns |
|----------|---------|
| `GET /trials/{t}/adverse-events/{aeId}/precedents` | Similar past AEs — feature vector + semantic similarity |
| `GET /trials/{t}/deviations/{devId}/precedents` | Similar past protocol deviations |
| `GET /trials/{t}/amendments/{amendmentId}/precedents` | Similar past protocol amendments |
| `GET /trajectory` | AE trajectory precedents |
| `GET /trajectory/matches` | Site enrollment trajectory matches |
| `GET /enrollment-trajectory` | Trial completion site trajectory precedents |

Query parameters: `limit` (default 5, max 20), `minScore` (default 0.5). All endpoints require `TrialMembership` check.

### Web UI

Lit-based web UI built with casehub-blocks-ui components and casehub-pages. Served via Quinoa (esbuild, hot-reload in dev mode).

- **Work Queue** — compliance officer work queue with `work-item-inbox`
- **Safety Workbench** — adverse event management with `approval-gate`, `sla-indicator`, `data-table`
- **Protocol Workbench** — protocol deviation and amendment management
- **Operations** — operational dashboard with `kpi-metric-row` for trial metrics

### RBAC

Four roles: `SPONSOR`, `INVESTIGATOR`, `COORDINATOR`, `MONITOR` (defined in `ClinicalGroups` in `api/`). All REST endpoints enforce `@RolesAllowed`. OIDC-based identity via `casehub-platform-oidc`.

## Dependencies

```
casehub-clinical
  -> casehub-engine                  (IRB gate, AE escalation, CasePlanModel, stage gating)
  -> casehub-engine-work-adapter     (HumanTaskScheduleHandler + WorkItemLifecycleAdapter)
  -> casehub-engine-scheduler-quartz (Quartz worker execution)
  -> casehub-platform                (runtime scope — @DefaultBean mocks for engine CDI wiring)
  -> casehub-platform-expression     (runtime scope — JQEvaluator for engine expression evaluation)
  -> casehub-ledger                  (FDA Merkle audit, GDPR erasure, EU AI Act Art.12, trust scoring)
  -> casehub-work                    (IRB/PI WorkItems with SLA and escalation)
  -> casehub-qhorus                  (COMMAND to PI, commitment lifecycle, safety agent channels)
  -> casehub-connectors-core         (sponsor and safety officer notification delivery)
  -> casehub-platform-memory-jpa     (prod — JPA CaseMemoryStore)
  -> casehub-platform-memory-inmem   (test scope — @Alternative CaseMemoryStore)
  -> casehub-engine-ledger           (TrustWeightedAgentStrategy, WorkerDecisionEventCapture, TrustScoreCache)
  -> casehub-platform-oidc           (RBAC: OidcCurrentPrincipal, @RolesAllowed enforcement)
  -> casehub-blocks                  (CBR: RoutingFeatureExtractor SPI — ClinicalRoutingFeatureExtractor)
  -> casehub-blocks-ui               (Web UI: data-table, approval-gate, sla-indicator, kpi-metric-row, work-item-inbox)
  -> casehub-pages                   (Web UI: page(), tree(), loadSite() — Quinoa frontend integration)
```

## The Compliance Gap It Closes

ClinicalAgent (peer-reviewed baseline, arXiv 2404.14777) structurally cannot provide:

- **Adverse event SLA enforcement** (GCP: serious events within 24h) — WorkItem `claimDeadline`
- **Protocol deviation authorisation by named PI** — COMMAND commitment lifecycle
- **Consent withdrawal** (GDPR Art.17) — ledger erasure and decision context sanitisation
- **Multi-site independence with trial-level rollup** — sub-case orchestration
- **FDA tamper-evident audit trail** — Merkle MMR + Ed25519-signed checkpoints
- **Trust-weighted safety agent routing** — Bayesian Beta from outcome attestations
- **Adaptive protocol paths** — IRB gate and grade-based AE escalation via CasePlanModel
- **IND deadline enforcement** — exact absolute FDA deadlines with two-tier breach escalation

## What It Does NOT Own

- Case orchestration engine, plan models, bindings — **casehub-engine**
- Work items, SLA tracking, escalation policies — **casehub-work**
- Messaging channels, COMMAND/RESPONSE lifecycle, commitments — **casehub-qhorus**
- Merkle audit trail, ledger erasure, compliance supplements — **casehub-ledger**
- Connector delivery (Slack, SMS, WhatsApp) — **casehub-connectors**
- Trust scoring infrastructure, Bayesian Beta computation — **casehub-engine-ledger**
- CBR case memory, similarity search, feature schemas — **casehub-blocks**
- UI component library (data-table, approval-gate, etc.) — **casehub-blocks-ui**
- Page rendering, site loading, dataset binding — **casehub-pages**
