# clinical Workspace

**Project repo:** /Users/mdproctor/claude/casehub/clinical
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/clinical` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| java-update-design / update-primary-doc | `design/JOURNAL.md` (created by `epic`) |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | workspace   | |
| blog       | workspace   | |
| design     | workspace   | |
| snapshots  | workspace   | |
| specs      | workspace   | |
| handover   | workspace   | |

---

# casehub-clinical — Claude Code Project Guide

## Platform Context

This repo is one component of the casehubio multi-repo platform. **Before implementing anything — any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol.**

**Platform architecture (fetch before any implementation decision):**
```
https://raw.githubusercontent.com/casehubio/parent/main/docs/PLATFORM.md
```

**Foundation repo deep-dives:**
- casehub-engine: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-engine.md`
- casehub-ledger: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-ledger.md`
- casehub-work: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-work.md`
- casehub-qhorus: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-qhorus.md`
- casehub-connectors: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-connectors.md`

---

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image target)

---

## What This Project Is

`casehub-clinical` is the **clinical trial coordination application** built on the CaseHub platform foundation. It is the market entry demonstration for CaseHub in regulated healthcare — showing that GCP, FDA, and GDPR requirements cannot be met by workflow-based LLM systems and are structurally satisfied by CaseHub's foundation.

This is an **application layer**, not a framework. The foundation provides coordination, accountability, audit, and compliance primitives. casehub-clinical provides the clinical trial domain logic: what a trial protocol is, how a site manages patient enrollment, how adverse events escalate, and how the FDA audit trail is constructed.

### Why Clinical Trials

Clinical trials operate under the strictest regulated AI requirements of any domain: GCP (ICH E6(R3)), FDA IND requirements, EMA CTR, and GDPR for patient data. Every agent decision must be traceable. Every protocol deviation must be authorised by a named Principal Investigator with a formal commitment. Every adverse event has a hard reporting deadline (24 hours for serious events, 7 days for others) with documented escalation.

The specific compliance gap ClinicalAgent (arXiv 2404.14777) — the peer-reviewed open-source baseline — cannot close by adding features:

| GCP / ICH / FDA requirement | ClinicalAgent | casehub-clinical |
|---|---|---|
| Adverse event SLA — serious within 24h, others within 7 days | No deadline tracking | WorkItem `claimDeadline` with auto-escalation |
| Protocol deviation authorisation — PI must formally approve | Agent decides autonomously; no named responsible party | COMMAND from PI required; commitment lifecycle tracks acknowledgement and resolution |
| Consent withdrawal cascade — GDPR Art.17 patient data erasure | No GDPR capability | `LedgerErasureService` + `DecisionContextSanitiser` SPI |
| Multi-site independence — 50+ sites with independent rollup to trial level | Single-case linear pipeline | Sub-case orchestration per site with trial-level aggregation |
| Tamper-evident audit — FDA audit trail independently verifiable | No audit trail | Merkle Mountain Range + Ed25519-signed checkpoints |
| Trust-weighted safety agent routing — reliable agents on high-risk decisions | No trust model | Bayesian Beta + EigenTrust via `TrustWeightedSelectionStrategy` |

**Comparison baseline:** ClinicalAgent ([arXiv 2404.14777](https://arxiv.org/abs/2404.14777), ACM BCB '24, GitHub open source). Full gap analysis in `docs/use-case-analysis.md` in casehub-parent (§8.1). Scored 24/25 on market fit — highest of all use cases.

---

## Layering Rule

This is an application, not a framework. If the capability requires knowledge of clinical trial protocols, GCP, FDA IND, or patient consent, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation.

---

## Reference Documents (in casehub-parent)

| Document | What it covers |
|----------|---------------|
| `https://raw.githubusercontent.com/casehubio/parent/main/docs/use-case-analysis.md` | Use case scoring, clinical trial selection rationale (§8.1), GCP compliance gap analysis |
| `https://raw.githubusercontent.com/casehubio/parent/main/docs/tutorial-strategy.md` | Clinical trial showcase scenario (§7), multi-site demonstration design, ClinicalAgent comparison |

---

## What casehub-clinical Must Build

### Domain Model

**Trial entities:**
- `ClinicalTrial` — the trial: `{protocolId, phase, sponsor, sites[], status, trialLevelStatus}`
- `TrialSite` — one investigator site: `{siteId, investigatorId, patients[], status}`
- `PatientEnrollment` — per-patient: `{patientId, eligibilityCriteria[], consentStatus, safetyEvents[]}`
- `ProtocolDeviation` — recorded deviation: `{deviationType, severity, piApprovalStatus, reportedAt}`
- `AdverseEvent` — safety event: `{severity, reportedAt, slaDeadline, escalationStatus}`
- `IrbApproval` — IRB/ethics gate: `{reviewType, committeeId, decisionDeadline, decision}`

**Capability tags:**
- `eligibility-screening` — assess patient against protocol inclusion/exclusion criteria
- `safety-monitoring` — detect and classify adverse events by severity
- `protocol-review` — assess proposed protocol deviations
- `irb-consultation` — IRB/ethics committee WorkItem gate
- `pi-authorisation` — Principal Investigator COMMAND (protocol deviations require PI commitment)
- `data-safety-monitoring` — DSMB-level safety review (independent of site)
- `regulatory-submission` — FDA/EMA submission preparation and traceability
- `trial-supervisor` — LLM supervisor: protocol amendment analysis, cross-site pattern detection

**Trust dimensions:**
- `safety-accuracy` — adverse event classification accuracy vs subsequent safety outcomes
- `eligibility-precision` — false positive rate on eligibility screening (patients excluded who should have enrolled)
- `protocol-adherence` — track record of flagging deviations vs missing them

### Trial Coordination CasePlanModel

Goals:
- `enrollment-complete` — target patient count reached across all sites
- `safety-monitoring-active` — all enrolled patients have active safety monitoring
- `regulatory-compliant` — all required reporting obligations met within SLA

Key bindings (site-level sub-case):
- `eligibility-screening` fires on new patient registration
- `adverse-event-escalation` fires when safety-monitoring reports Grade ≥ 3 event — 24h WorkItem SLA
- `pi-authorisation-required` fires on protocol deviation — COMMAND to PI, creates formal Commitment
- `irb-consultation` fires when PI-authorised deviation requires ethics review — 72h WorkItem
- `dsmb-review` fires on accumulation of safety signals across sites — trial-level sub-case coordination

### Multi-Site Sub-Case Structure

```
Trial case (parent)
├── Site A sub-case
│   ├── Patient enrollment bindings
│   ├── Adverse event monitoring bindings
│   └── Protocol deviation bindings
├── Site B sub-case
│   └── ...
└── Trial-level rollup binding (aggregates site sub-cases)
    → DSMB review triggers when safety signal threshold crossed across ≥ 2 sites
```

Trial-level binding fires on aggregated context from all site sub-cases — no site-level agent reasons about this; the engine detects the cross-site pattern from accumulated blackboard state.

### Foundation Gates

| Capability | Foundation prerequisite |
|-----------|------------------------|
| Site-level sub-case orchestration | Sub-case ✅ DONE (engine#195) |
| Adverse event SLA WorkItem | casehub-work ✅ production |
| PI authorisation commitment lifecycle | P0 complete (engine#186 ✅, qhorus ✅) |
| GDPR consent withdrawal (Art.17) | LedgerErasureService ✅ |
| FDA Merkle audit trail | CaseLedgerEntry ✅ (2026-04-26) |
| EU AI Act Art.12 ComplianceSupplement | casehub-ledger ✅ |
| Trust-weighted safety agent routing | P1.3 TrustWeightedSelectionStrategy wired in engine |
| LLM protocol amendment supervisor | LlmPlanningStrategy SPI (engine) |
| HITL WorkItem → case signal (IRB gate) | casehub-work-adapter wiring pending |

### Showcase Scenario

3-site oncology trial. Site A enrolls a patient — agents run eligibility screening across 12 criteria. A marginal criterion triggers an IRB consultation (WorkItem: 72-hour SLA). At Site B, a Grade 3 adverse event fires automatic 24-hour safety escalation. At Site C, a protocol amendment is proposed — the LLM supervisor reads accumulated context from all three sites and recommends whether to proceed. The Merkle audit trail means FDA can independently verify the complete decision chain for every patient at every site.

ClinicalAgent runs as a linear pipeline for one site. It has no concept of SLA, no IRB gate, no adverse event escalation, and no audit trail.

---

## Ecosystem Conventions

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:**
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Java on this machine:**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26)
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home  # native only
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

---

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/clinical

**Automatic behaviours:**
- Before implementation begins — check for an active issue. If none, run issue-workflow Phase 1 before writing any code.
- Before any commit — confirm issue linkage.
- All commits reference an issue — `Refs #N` or `Closes #N`.
