# 0004 — Three-Phase Pattern for @Transactional + startCase().join()

Date: 2026-05-25
Status: Accepted

## Context and Problem Statement

`TrialActivationService` must validate and update domain state (requires a
`@Transactional` boundary), start an engine case via `startCase().join()`
(async, blocks until CaseStartedEventHandler replies), and persist the
returned caseId (requires another `@Transactional` boundary). Doing all
three in a single `@Transactional` method causes a connection-pool deadlock
in production.

## Decision Drivers

* Agroal holds a JDBC connection from the first Panache call to transaction
  commit — `join()` must not run while that connection is held
* `casehub-engine-persistence-hibernate` (production) uses JDBC for
  `CaseStartedEventHandler`; same pool as the domain datasource
* Tests use `casehub-engine-persistence-memory` (no JDBC) — the deadlock
  is completely invisible in the test suite

## Considered Options

* **Option A — Single @Transactional method**: status change, `startCase().join()`,
  caseId persist in one transaction; simplest code, deadlocks under pool exhaustion
* **Option B — Three-phase**: commit status → `join()` outside any transaction →
  commit caseId; `activate()` itself not `@Transactional`
* **Option C — Async persist via thenAccept()**: fire `startCase()` async, update
  caseId in a callback transaction; no blocking on the caller thread

## Decision Outcome

Chosen option: **Option B**, because it provably avoids holding a connection across
the blocking `join()` and produces a clear reference implementation. Option C adds
asynchronous complexity (callback transaction, race conditions in tests) with no
structural benefit.

### Positive Consequences

* Deadlock eliminated regardless of Agroal pool size
* Pattern is explicit and reproducible: any service that calls `startCase().join()`
  in future must follow this template
* `TrialActivationService` serves as the canonical reference for this pattern

### Negative Consequences / Tradeoffs

* Three database round-trips instead of one (acceptable: activation is infrequent)
* If `startCase()` throws, the domain status is ACTIVE but no engine case exists;
  a retry from ACTIVE status returns 409 (idempotent but misleading)

## Pros and Cons of the Options

### Option A — Single @Transactional

* ✅ Fewest lines of code
* ❌ Deadlock under pool exhaustion in production; zero indication in tests

### Option B — Three-phase (chosen)

* ✅ Provably safe: no connection held across `join()`
* ✅ Clear reference pattern for future service authors
* ❌ Three round-trips instead of one (negligible for an activation endpoint)
* ❌ Partial-state risk if `startCase()` throws (domain ACTIVE, no engine case)

### Option C — Async via thenAccept()

* ✅ Non-blocking
* ❌ Callback transaction adds async complexity
* ❌ Harder to test reliably (timing-dependent)

## Links

* `runtime/.../service/TrialActivationService.java` — reference implementation
* GE-20260525-6f8b88 (garden) — full deadlock analysis
* CLAUDE.md — "Engine case activation — three-phase pattern" convention entry
