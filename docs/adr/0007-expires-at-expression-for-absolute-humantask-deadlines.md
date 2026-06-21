# 0007 — Absolute deadline expression for humanTask WorkItems

Date: 2026-06-22
Status: Accepted

## Context and Problem Statement

The FDA IND expedited safety reporting deadline is `ae.reportedAt + window` — an
absolute timestamp derived from domain data. The engine's `HumanTaskTarget` only
supported `expiresIn` (a `Duration` measured from WorkItem creation time) and
`claimDeadlineHours` (integer business hours). Neither can express a deadline
anchored to a domain event that happened before the WorkItem was created, making
it structurally impossible to enforce FDA deadlines without introducing drift
proportional to the delay between AE report and case start.

## Decision Drivers

* FDA deadline must be exact (`ae.reportedAt + 7/15 days`), not approximate
* Mechanism must be YAML-declarative — deadline is a property of the binding,
  not of service-layer Java code
* Expression language must be pluggable — not hardcoded to JQ
* No engine changes should be required in consuming applications

## Considered Options

* **Option A — PropagationContext budget** — compute `budget = deadline - now()`
  at case start; pass as `PropagationContext`; `caseBudgetDeadline` caps WorkItem
  `expiresAt`
* **Option B — `expiresAtExpression` SPI** — new JQ (or pluggable) expression on
  `HumanTaskTarget`, evaluated at scheduling time against case context WORKING panel;
  result parsed as ISO-8601 Instant; folded into `earliestOf` chain
* **Option C — Java function worker** — ClinicalRegulatorySubmissionCaseHub overrides
  `getDefinition()` to register a Java function worker that creates the WorkItem
  programmatically with the absolute `expiresAt`

## Decision Outcome

Chosen option: **Option B — `expiresAtExpression` SPI**, because:
1. YAML-declarative — deadline intent is expressed where the binding is defined,
   not in a service class
2. Pluggable — `ExpressionEngine.extractString()` is a default-throws SPI method;
   any engine implementation can override it; no `instanceof` anywhere
3. Evaluated at scheduling time from the full case context — correct regardless
   of how long between case start and humanTask dispatch
4. `HumanTaskScheduleHandler` stays expression-free — `expiresAtDeadline` is
   resolved upstream and passed in `HumanTaskScheduleEvent`

### Positive Consequences

* Any humanTask binding in any harness can now express absolute deadline
  enforcement declaratively via `expiresAtExpression: ".fieldName"`
* JQ syntax is validated at YAML load time — invalid expression fails at startup,
  not silently at runtime as a null deadline
* `earliestOf(taskDeadline, expiresAtDeadline, caseBudgetDeadline)` preserves all
  existing deadline sources; new source is additive

### Negative Consequences / Tradeoffs

* `HumanTaskScheduleEvent` is a Java record — adding `expiresAtDeadline: Instant`
  is a BREAKING change; all constructor callsites must be updated (mechanical)
* `expiresAtExpression` evaluates against WORKING panel — a deadline in a different
  context panel would silently return `Optional.empty()` (same failure mode prevented
  by load-time JQ syntax validation)
* New `ExpressionEngine.extractString()` SPI method — any custom `ExpressionEngine`
  bean that doesn't override it gets `Optional.empty()` + WARN (graceful, not crash)

## Pros and Cons of the Options

### Option A — PropagationContext budget

* ✅ No new engine API surface
* ❌ Semantic mismatch — encodes absolute deadline as resource budget
* ❌ Clock drift: `budget = deadline - now()` → `deadline = now() + budget`;
  two `Instant.now()` calls introduce unbounded drift
* ❌ Requires Java service changes for YAML-declarative information
* ❌ `caseBudgetDeadline` caps ALL humanTask bindings in the case — correct here,
  but only by accident; cannot express per-binding absolute deadlines

### Option B — `expiresAtExpression` SPI (chosen)

* ✅ YAML-declarative, evaluated at scheduling time
* ✅ Pluggable via `ExpressionEngine.extractString()` default-throws SPI
* ✅ Exact — evaluates the stored ISO-8601 string, no clock arithmetic
* ❌ Requires new engine SPI method (`extractString`) and record field (breaking)

### Option C — Java function worker

* ✅ No engine changes
* ❌ Bypasses YAML DSL — deadline intent not co-located with binding definition
* ❌ Couples WorkItem creation to the CaseHub subclass; no benefit from
  engine's `HumanTaskScheduleHandler` wiring (callerRef, PlanItem lifecycle)
* ❌ Does not generalise — each harness that needs absolute deadlines must
  duplicate the same pattern in a Java override

## Links

* casehubio/engine#549 — implementation
* casehubio/clinical#83 — consumer (IND deadline enforcement)
* LAYER-LOG.md Layer 10 — context for the clinical application
