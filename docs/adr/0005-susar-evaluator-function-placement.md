# 0005 — SusarEvaluatorFunction interface placement in runtime/service/

Date: 2026-06-12
Status: Accepted

## Context and Problem Statement

`SusarEvaluatorFunction extends Function<Map<String,Object>,WorkerResult>` is a named CDI interface establishing the displacement contract for the SUSAR criteria evaluator. The `consumer-spi-placement.md` protocol directs consumer-facing SPI interfaces to `api/spi/`. However, `WorkerResult` is defined in `casehub-engine-api`, which is not — and must not be — a dependency of `clinical-api` (the `api/` module is documented as "zero JPA, zero Quarkus, zero engine types").

## Decision Drivers

* `clinical-api` must stay pure Java — no framework or engine dependencies
* CDI disambiguation requires a named interface (not raw `Function<Map,WorkerResult>`) to prevent generic-erasure `AmbiguousResolutionException`
* CDI displacement contract must be preserved for future ML agent implementation
* `consumer-spi-placement.md` protocol explicitly allows `runtime/` when only runtime provides implementations

## Considered Options

* **Option A** — Place in `api/spi/` (protocol default)
* **Option B** — Add `casehub-engine-api` as a dependency of `clinical-api`
* **Option C** — Place in `runtime/service/` (justified deviation from protocol)

## Decision Outcome

Chosen option: **Option C** — `runtime/service/SusarEvaluatorFunction.java`, because `WorkerResult` from `casehub-engine-api` cannot appear in `api/` without violating the pure-Java module contract.

### Positive Consequences

* `clinical-api` remains pure Java — zero framework dependencies
* CDI ambiguity prevention via named interface preserved
* `@DefaultBean` displacement contract works correctly at runtime scope

### Negative Consequences / Tradeoffs

* Deviates from `consumer-spi-placement.md` protocol default — documented here as a justified exception
* Future ML agent implementing the interface must depend on `clinical-runtime` (acceptable — any ML agent is application-tier and already depends on runtime)

## Pros and Cons of the Options

### Option A — `api/spi/` (protocol default)

* ✅ Follows consumer-spi-placement protocol
* ❌ Requires `WorkerResult` from `casehub-engine-api` — breaks pure-Java `api/` constraint

### Option B — Add `casehub-engine-api` to `clinical-api` deps

* ✅ Allows `api/spi/` placement
* ❌ Pulls engine types into a module documented as "zero JPA, zero Quarkus"
* ❌ Breaks consumer module isolation — any downstream depending on `clinical-api` alone would gain transitive engine deps

### Option C — `runtime/service/` (chosen)

* ✅ Preserves pure-Java `api/` module constraint
* ✅ Named interface prevents CDI generic-erasure ambiguity
* ✅ `consumer-spi-placement.md` explicitly permits `runtime/` when only runtime provides implementations
* ❌ Slight deviation from the default protocol placement guidance

## Links

* `docs/specs/2026-06-11-action-risk-classifier-design.md` — spec documenting the placement rationale
* `consumer-spi-placement.md` (casehub garden protocol) — the protocol this deviates from
* `casehubio/clinical#47` — Layer 8 implementation issue
