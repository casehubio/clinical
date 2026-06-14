# 0006 — Dedicated SUSAR Oversight Case Hub over Inline ae-escalation Binding

Date: 2026-06-13
Status: Accepted

## Context and Problem Statement

SUSAR criteria evaluation (Layer 8) must fire when an AE is reported and gate on a
qualified investigator before the 7-day regulatory clock starts. The original implementation
added `SusarCriteriaEvaluator` as a programmatic `ContextChangeTrigger` binding on the
existing `ClinicalAdverseEventCaseHub` (ae-escalation case). This failed: the engine fires
the first `CaseContextChangedEvent` before the initial context is applied, so the binding
fires against an empty context where `.susarAssessmentComplete == null` is trivially true.
`PlanningStrategyLoopControl` then marks the plan item RUNNING, permanently blocking
re-dispatch.

## Decision Drivers

* Engine event timing: `WorkerScheduleEventHandler` reads context LIVE from `CaseInstance`,
  not from the event snapshot — the first event always sees empty context
* SUSAR assessment must fire independently of the AE safety review and DSMB escalation
  bindings (which must remain active regardless of SUSAR outcome)
* The oversight gate WorkItem must be independently verifiable in the FDA audit trail

## Considered Options

* **Option A** — Inline programmatic binding on ae-escalation case — original approach
* **Option B** — Fix the engine timing (delay context application before first event)
* **Option C** — Dedicated SUSAR oversight case hub started by a service only when criteria confirmed

## Decision Outcome

Chosen option: **Option C**, because it avoids the engine timing bug entirely by starting
the oversight case only after Phase 1 confirms criteria are met (`susarRequired=true`),
ensuring the case context is never empty when the YAML binding evaluates.

### Positive Consequences

* Binding filter `.aeId != null and .susarAssessmentComplete == null` is false on the empty
  first-context event — engine timing bug is unreachable
* SUSAR oversight lifecycle is independently auditable (separate case, separate ledger subject)
* `SusarGateDecisionListener` can discriminate SUSAR gates by querying
  `AdverseEvent.findBySusarOversightCaseId` — race-free, restart-safe
* `SusarOversightCaseService` has an idempotency guard and explicit `SusarOversightStatus`
  enum — NONE / REQUESTED / COMPLETED / FAILED

### Negative Consequences / Tradeoffs

* Two services now observe `AdverseEventReportedEvent` concurrently — ordering is CDI
  async, non-deterministic. This is acceptable; both are idempotent.
* `SusarCriteriaEvaluator` runs twice (once in Phase 1 for the gate check, once as the
  oversight case worker) — minor DB overhead, correct behaviour.

## Pros and Cons of the Options

### Option A — Inline ae-escalation binding

* ✅ Single case hub, simpler code
* ❌ Fires on empty first-context event → RUNNING state locks, worker never dispatches
* ❌ `susarAssessmentComplete` entangled with safety-review and dsmb-escalation goals

### Option B — Engine timing fix

* ✅ Fixes the root cause
* ❌ Requires modifying casehub-engine internals; casehub-clinical cannot own that fix
* ❌ Engine fix might have side effects on other case hubs that rely on first-event behaviour

### Option C — Dedicated oversight case hub

* ✅ Avoids engine timing bug without requiring engine changes
* ✅ Gate discrimination via DB is race-free (vs CaseInstanceCache approach)
* ✅ Independent audit lifecycle per SUSAR assessment
* ❌ Extra service + case hub class; slightly more code

## Links

* casehubio/clinical#77 — SUSAR dedicated oversight case hub
* docs/specs/2026-06-12-susar-fix-gdpr-design.md
* [ADR-0005](0005-susar-evaluator-function-placement.md) — SusarEvaluatorFunction placement
