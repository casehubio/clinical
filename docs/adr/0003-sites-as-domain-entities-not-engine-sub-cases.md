# 0003 — Investigational Sites as Domain Entities, Not Engine Sub-Cases

Date: 2026-05-25
Status: Accepted

## Context and Problem Statement

Layer 6 adds trial-level coordination. A natural question is whether each
investigational site should be a CaseInstance child of the trial case (the
engine sub-case model) or remain a plain domain entity. The engine's sub-case
facility was added specifically for this layer.

## Decision Drivers

* Engine sub-cases are designed for bounded delegation: parent delegates work
  to child, child runs and completes, parent is notified
* Investigational sites are long-running parallel operations (months to years)
  with no meaningful terminal state during an active trial
* Every site-level binding (AE escalation, IRB gate, PI authorisation) is
  already handled by dedicated per-event engine cases (Layer 5)
* The tutorial value of Layer 6 is cross-site pattern detection, not hierarchical
  case composition

## Considered Options

* **Option A — Site sub-cases**: Each `TrialSite` spawns a child `CaseInstance`
  when activated; parent trial case orchestrates via sub-case hierarchy
* **Option B — Domain entities with trial-level signal**: Sites remain `TrialSite`
  JPA entities; the trial case accumulates per-site signals via `runtime.signal()`
* **Option C — Independent site cases (no parent-child)**: Each site gets its own
  `CaseInstance` but unrelated to the trial case; application code correlates them

## Decision Outcome

Chosen option: **Option B**, because sites do not fit the sub-case lifecycle —
there is no meaningful "site completion" event, and no site-level bindings remain
that the engine would own. The tutorial value is in the trial blackboard detecting
a cross-site safety pattern autonomously from accumulated `grade4Active` signals.

### Positive Consequences

* No misuse of the sub-case model for a lifecycle it wasn't designed for
* LAYER-LOG demonstrates the blackboard pattern (cross-case signal accumulation)
  rather than structural hierarchy, which is more universally applicable
* Layer 5 code (per-event cases) unchanged — no refactoring required
* Sub-case composition available for a future layer (patient batch screening)

### Negative Consequences / Tradeoffs

* The trial case does not have structural knowledge of which sites are enrolled —
  it only knows which sites have active Grade 4+ flags
* Epic 3 was titled "multi-site sub-case structure" — the completed design resolves
  it without an engine parent-child relationship; this may surprise readers expecting
  sub-cases

## Pros and Cons of the Options

### Option A — Site sub-cases

* ✅ Demonstrates engine's sub-case capability explicitly
* ❌ Sub-case lifecycle mismatch: sites don't "complete"
* ❌ Site sub-cases would have no bindings of their own — structural containers only
* ❌ Dynamic site count requires app-driven spawning, not declarative YAML

### Option B — Domain entities with trial-level signal (chosen)

* ✅ Correct tool for the problem: domain entities for long-lived domain objects
* ✅ `runtime.signal()` is the designed cross-case communication mechanism
* ✅ No Layer 5 refactoring
* ❌ Trial case has no structural awareness of active sites — only flag state

### Option C — Independent site cases

* ✅ Each site has its own state machine
* ❌ Application code must correlate site cases to trial — more coupling, no engine benefit
* ❌ Doesn't use or demonstrate the parent-child engine facility

## Links

* Layer 6 design spec: `specs/2026-05-25-layer-6-trial-coordination-design.md` (workspace)
* LAYER-LOG.md — Layer 6 entry
* Closes casehubio/clinical#3 (Epic 3: multi-site sub-case structure)
