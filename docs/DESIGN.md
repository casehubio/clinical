# casehub-clinical — Design

## Architecture

casehub-clinical is a layered agentic harness for clinical trial coordination.
Foundation modules are adopted one at a time; each layer adds a new capability
that the previous layer could not provide.

**Layer 5 (casehub-engine):** Per-event engine cases handle adaptive protocol paths.
AE escalation cases route Grade 3 and Grade 4+ events through different gate
combinations via YAML `contextChange.filter` bindings. IRB consultation suspends a
deviation review case in WAITING until a committee decision arrives.

**Layer 6 (trial coordination):** A trial-level `CaseInstance` (`trial-coordination.yaml`)
starts when the trial transitions to ACTIVE. Site-level AE escalation services signal
the trial case via `runtime.signal(trialCaseId, "grade4Active.<siteId>", Boolean)` —
the engine's designed cross-case communication mechanism. The trial case's DSMB
rollup binding fires when ≥2 sites are simultaneously flagged:
`[.grade4Active // {} | to_entries[] | select(.value == true)] | length >= 2`.
Sites are domain entities, not sub-cases — the sub-case model is reserved for bounded
delegated work with a terminal lifecycle.

## Module Structure

| Module | Type | Purpose |
|--------|------|---------|
| `api` | Pure Java | Domain events, SPIs, model enums |
| `runtime` | Quarkus | Entities, services, resources, YAML case definitions |

## Key Abstractions

**Trial coordination (Layer 6):**

- `ClinicalTrialCaseHub` — `YamlCaseHub` extension loading `trial-coordination.yaml`
- `TrialActivationService` — three-phase activation: commit status → `startCase().join()` outside any transaction → commit returned caseId. Required to avoid Agroal pool deadlock when engine JPA persistence uses the same pool.
- `TrialCaseLookup` — resolves site → trial → `engineCaseId` for signal routing
- `TrialSafetySignalService` — owns all grade4 blackboard flag operations: `signalGrade4Active(siteId)` sets the flag when a Grade 4+ AE starts; `onAeEscalationCompleted` clears it on AE case completion. All direct `runtime.signal()` calls for grade4 flags route through this service.

**AE escalation (Layer 5):**

- `ClinicalAdverseEventCaseHub` / `ae-escalation.yaml` — adaptive safety routing (Grade 3: senior monitor gate; Grade 4+: + DSMB gate in parallel)
- `AeEscalationCaseService` — starts AE cases; delegates Grade 4+ trial flag to `TrialSafetySignalService.signalGrade4Active()`
- `AeEscalationListener` — observes `CaseLifecycleEvent("CaseCompleted")` to write ledger entry and fire domain events

**Grade threshold:** `SEVERE_GRADES = Set.of(GRADE_4, GRADE_5)` — shared constant in both signal services; avoids `ordinal()` comparison.

## SPI Contracts

| SPI | Module | Purpose |
|-----|--------|---------|
| `AdverseEventEscalationPolicy` | `api` | Determines which escalation gates apply for a given AE |
| `AdverseEventEscalationRequirements` | `api` | Return type: `requiresSeniorMonitor`, `requiresDsmbEscalation` |
| `AeEscalationCompletedEvent` | `api` | CDI event on AE case completion; carries `aeId`, `grade`, `siteId`, `safetyReviewOutcome`, `dsmbEscalated` |
| `IrbCommitteeAssignmentPolicy` | `api` | Maps deviation context (`IrbCommitteeContext`: deviationId, siteId, trialId, severity) to an `IrbCommitteeAssignment` (committeeId, candidateGroups). `DefaultIrbCommitteeAssignmentPolicy` returns `"irb-committee"`. Override with `@Alternative @ApplicationScoped` — do NOT add `@Priority` in test scopes (CDI 2.0 global activation). |

**`AeEscalationCompletedEvent.siteId`** was added in Layer 6 to enable `TrialSafetySignalService` to clear the trial's `grade4Active` flag without a downstream DB lookup.

## Data Model

**Key field additions (Layer 6):**

- `ClinicalTrial.engineCaseId UUID` — set when trial transitions to ACTIVE; null until then. V110 migration (`db/migration/default/`).

**Entities:** `ClinicalTrial`, `TrialSite`, `PatientEnrollment`, `AdverseEvent`, `ProtocolDeviation`, `IrbApproval`

## Configuration

Engine case activation — three-phase pattern is required for any `@Transactional` service that calls `startCase().join()`. See `TrialActivationService` and ADR 0004.

YAML binding conditions must use `on.contextChange.filter`, not `when`. The `when` field is silently ignored for `contextChange` triggers (GE-20260523-fd8725).
