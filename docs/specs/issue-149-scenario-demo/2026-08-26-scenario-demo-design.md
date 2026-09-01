# Clinical Trial Demo Scenario — Design Spec

**Date:** 2026-08-26
**Branch:** issue-149-scenario-demo
**Decisions:** [decisions.md](decisions.md)

## Overview

Replace `DemoDataSeeder` (Java, programmatic) with a YAML scenario file driven by the pages scenario engine. The scenario mixes backend GraphQL actions (bulk data seeding) with browser ARIA automation (visual form demos) and narrative content (tutorial annotations + spotlight callouts).

### Goals

1. A single `clinical-trial-demo.yaml` replaces DemoDataSeeder
2. The demo drives real forms visually for accountability moments
3. Tutorial narration explains the governance story as it unfolds
4. The scenario doubles as both a demo and an integration test
5. DemoDataSeeder is deprecated once the scenario is verified equivalent

### Non-goals

- Replacing the existing workbench views (Safety, Protocol, Operations)
- Adding new domain entities or endpoints (all exist from #150)
- Controller UI development (pages #341 — separate concern)

---

## Architecture

### Integration pattern

Clinical adds `casehub-pages-scenario-client` as a runtime dependency. A new `ClinicalScenarioActions` CDI bean defines `@ScenarioAction` methods that call the service layer. The scenario engine's `GraphQLDispatcher` calls these via the `ScenarioExecutorClient` WebSocket bridge.

```
Scenario Controller (pages)
    │
    ├── GraphQL dispatch ──→ ClinicalScenarioActions (@ScenarioAction beans)
    │                            └── calls service layer (same as DemoDataSeeder)
    │
    └── ARIA dispatch ──→ Browser (pages-aria scenario handler)
                              └── fills forms, clicks buttons by ARIA role+name
```

### Scenario file

`runtime/src/main/resources/scenarios/clinical-trial-demo.yaml`

Loaded from classpath. Ships with the app as part of the reference architecture.

---

## @ScenarioAction Methods

New class: `runtime/src/main/java/io/casehub/clinical/scenario/ClinicalScenarioActions.java`

~10 actions mirroring DemoDataSeeder operations:

| Action | What it does | Returns |
|--------|-------------|---------|
| `createTrial` | Creates trial with protocol, phase, sponsor | `{ trialId }` |
| `activateTrial` | Moves trial from PLANNING to RECRUITING | `{ status }` |
| `addSite` | Adds investigator site to trial | `{ siteId }` |
| `enrollPatient` | Enrolls patient candidate at site | `{ enrollmentId }` |
| `screenPatient` | Screens patient with eligibility criteria | `{ screeningResult }` |
| `reportAdverseEvent` | Reports AE with grade, actuality, flags | `{ aeId, slaDeadline }` |
| `reportDeviation` | Reports protocol deviation with type/severity | `{ deviationId }` |
| `approvePiResponse` | Sends PI approval via qhorus channel | `{ approved }` |
| `approveSusarGate` | Approves SUSAR oversight gate WorkItem | `{ gateApproved }` |
| `verifyLedger` | Verifies Merkle chain for enrollment | `{ valid, merkleRoot }` |

Each method receives `ActionContext` with data params and calls the corresponding service (e.g., `AdverseEventService.reportAdverseEvent()`).

---

## Scenario Structure

Four chapters: Setup (bulk), Accountability (visual), AI Governance (visual), The Proof (visual).

### Chapter 1 — Trial Setup (bulk, invisible to audience)

```yaml
- label: "Trial Setup"
  content: |
    ## Phase III Clinical Trial
    Setting up a 3-site trial with AI agents for eligibility screening,
    safety monitoring, and protocol review.
  sections:
    - label: "Create Trial"
      steps:
        - name: create-trial
          target: server
          commands:
            - action: createTrial
              data:
                protocolId: "ONCO-2026-DEMO"
                phase: "PHASE_III"
                sponsor: "Acme Pharma"
                targetEnrollment: 100

    - label: "Add Sites"
      steps:
        - name: site-a
          target: server
          commands:
            - action: addSite
              data:
                trialId: "${create-trial.trialId}"
                investigatorId: "dr-chen"
        - name: site-b
          target: server
          commands:
            - action: addSite
              data:
                trialId: "${create-trial.trialId}"
                investigatorId: "dr-patel"

    - label: "Enroll Patients"
      steps:
        - name: patient-a1
          target: server
          commands:
            - action: enrollPatient
              mode: bulk
              data:
                items:
                  - { trialId: "${create-trial.trialId}", siteId: "${site-a.siteId}", patientId: "PAT-001" }
                  - { trialId: "${create-trial.trialId}", siteId: "${site-a.siteId}", patientId: "PAT-002" }
                  - { trialId: "${create-trial.trialId}", siteId: "${site-b.siteId}", patientId: "PAT-003" }

    - label: "Activate Trial"
      steps:
        - name: activate
          target: server
          commands:
            - action: activateTrial
              data:
                trialId: "${create-trial.trialId}"
```

### Chapter 2 — Accountability in Action (visual, ARIA forms)

```yaml
- label: "Accountability in Action"
  content: |
    ## When a Protocol Deviation Occurs
    A CRITICAL deviation is reported. Watch how the platform creates a
    formal obligation for the Principal Investigator — not a notification,
    a tracked commitment with a deadline.
  sections:
    - label: "Report Deviation"
      steps:
        - navigate: "/manage?tab=Trial+Detail&subtab=Deviations"
        - fill:
            role: textbox
            name: "Deviation Type"
            within: { role: form, name: "Report Deviation" }
            value: "CONSENT_DEVIATION"
        - select:
            role: combobox
            name: "Severity"
            within: { role: form, name: "Report Deviation" }
            value: "CRITICAL"
        - click:
            role: button
            name: "Report"
            within: { role: form, name: "Report Deviation" }
        - spotlight:
            role: row
            name: "CONSENT_DEVIATION"
            content: |
              The PI now has a formal COMMAND — a tracked obligation with
              a deadline. Not an email, not a notification — a commitment
              that the platform enforces.

    - label: "PI Authorisation"
      steps:
        - name: approve-pi
          target: server
          commands:
            - action: approvePiResponse
              data:
                deviationId: "${report-deviation.deviationId}"
        - spotlight:
            role: region
            name: "PI Commitment"
            content: |
              The commitment lifecycle tracks every state: COMMANDED →
              ACKNOWLEDGED → DONE. Every transition is a named actor
              with a timestamp in the Merkle chain.
```

### Chapter 3 — AI Governance (visual, ARIA forms + spotlights)

```yaml
- label: "AI Governance"
  content: |
    ## When the AI Makes a Safety Decision
    A Grade 4 adverse event is reported. Watch the platform route it
    to a trusted safety agent, evaluate SUSAR criteria, and gate the
    AI's decision for human approval.
  sections:
    - label: "Report Grade 4 AE"
      steps:
        - navigate: "/manage?tab=Trial+Detail&subtab=Patients"
        - fill:
            role: combobox
            name: "Grade"
            within: { role: form, name: "Report Adverse Event" }
            value: "GRADE_4"
        - fill:
            role: textbox
            name: "Occurred At"
            within: { role: form, name: "Report Adverse Event" }
            value: "2026-08-26T10:00"
        - click:
            role: button
            name: "Submit"
            within: { role: form, name: "Report Adverse Event" }

    - label: "AI Decision vs Platform Governance"
      content: |
        The AI agent classified this as a Suspected Unexpected Serious
        Adverse Reaction (SUSAR). But the platform doesn't trust the
        AI blindly — it gates the decision for human review.
      steps:
        - spotlight:
            targets:
              - role: region
                name: "AI Decision"
                content: "The AI's SUSAR determination"
                position: bottom
              - role: region
                name: "Oversight Gate"
                content: "Human approval required before the decision takes effect"
                position: bottom

    - label: "Approve SUSAR Gate"
      steps:
        - name: approve-gate
          target: server
          commands:
            - action: approveSusarGate
              data:
                aeId: "${report-ae.aeId}"
        - spotlight:
            role: region
            name: "Trust Score"
            content: |
              The agent's trust score updated via Bayesian inference.
              Endorsed decisions increase the score; overridden decisions
              decrease it. This is how the platform learns which agents
              to trust on which types of decisions.
```

### Chapter 4 — The Proof (visual, verification)

```yaml
- label: "The Proof"
  content: |
    ## Independently Verifiable Audit Trail
    Every decision — human and AI — is in a tamper-evident Merkle chain.
    Any auditor can verify the complete history without trusting the platform.
  sections:
    - label: "Verify Merkle Chain"
      steps:
        - name: verify
          target: server
          commands:
            - action: verifyLedger
              data:
                trialId: "${create-trial.trialId}"
                enrollmentId: "${patient-a1.enrollmentId}"
        - spotlight:
            role: alert
            name: "Verification Result"
            content: |
              VERIFIED. The Merkle Mountain Range hash chain is intact.
              Every entry — from initial AE report through SUSAR gate
              approval — is cryptographically linked and independently
              verifiable. FDA 21 CFR Part 312 audit trail, built into
              the platform.
```

---

## Dependencies

### Maven

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-pages-scenario-client</artifactId>
</dependency>
```

### Pages features consumed

| Feature | Pages module | Status |
|---------|-------------|--------|
| @ScenarioAction + GraphQL dispatch | scenario-client + scenario-runtime | Landed |
| ARIA form automation | pages-aria | Landed |
| Narrative content per step | scenario model | Landed (#342) |
| Spotlight callout overlay | pages-aria | Landed (#357) |
| RestStep (alternative to GraphQL) | scenario model | Landed (#356) |

---

## DemoDataSeeder Deprecation

Once the scenario produces equivalent results (same ledger entries, same trust scores, same entity state):

1. Add `@Deprecated` to `DemoDataSeeder`
2. Remove `@IfBuildProfile("dev")` — the scenario engine replaces it
3. File a follow-up issue to delete the class after one release cycle

Verification: run both DemoDataSeeder and the scenario, compare entity counts and ledger entry hashes.

---

## Testing Strategy

### Scenario as integration test

The scenario YAML itself is an integration test — it creates entities, drives forms, and asserts results. Run via:

```bash
mvn test -pl runtime -Dtest=ScenarioSmokeTest
```

`ScenarioSmokeTest` loads the scenario YAML, executes all server-target steps (skip ARIA browser steps in headless mode), and asserts:
- Trial exists with correct phase/sponsor
- 3 sites, 3 patients enrolled
- AE reported with correct grade
- Deviation reported with correct severity
- Ledger verification returns valid

### @ScenarioAction unit tests

Each action method tested independently with mocked services — same pattern as existing service tests.

---

## Build Order

1. **Add scenario-client dependency** to runtime pom.xml
2. **Create ClinicalScenarioActions** — @ScenarioAction methods calling service layer
3. **Unit tests** for each action method
4. **Write scenario YAML** — clinical-trial-demo.yaml with 4 chapters
5. **ScenarioSmokeTest** — headless execution of server-side steps
6. **Deprecate DemoDataSeeder** after equivalence verification

---

## References

- [ScenarioStep.java](pages/backend/scenario/src/main/java/io/casehub/pages/scenario/ScenarioStep.java) — step model (AriaStep, GraphQLStep, SimulatedStep)
- [ScenarioExecutorClient.java](pages/backend/scenario-client/src/main/java/io/casehub/pages/scenario/client/ScenarioExecutorClient.java) — consumer SDK
- [ScenarioAction.java](pages/backend/scenario-client/src/main/java/io/casehub/pages/scenario/client/ScenarioAction.java) — action annotation
- [AriaDispatcher.java](pages/backend/scenario-runtime/src/main/java/io/casehub/pages/scenario/runtime/AriaDispatcher.java) — browser command dispatch
- [scenario-handler.ts](pages/packages/pages-aria/src/server/scenario-handler.ts) — browser-side ARIA executor
- [hybrid-helpdesk.yaml](pages/backend/scenario/src/test/resources/scenarios/hybrid-helpdesk.yaml) — reference hybrid scenario
- casehubio/clinical#150 — production forms (landed, ARIA-labelled)
- casehubio/casehub-pages#342 — narrative content (closed)
- casehubio/casehub-pages#357 — spotlight callout (closed)
- casehubio/casehub-pages#356 — RestStep (closed)
