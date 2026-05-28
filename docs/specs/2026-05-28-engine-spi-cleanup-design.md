# Design: Engine SPI cleanup + escalation status + engine_case_id + IRB committee SPI

**Branch:** `issue-28-engine-spi-cleanup`  
**Issues:** clinical#28, clinical#27, clinical#26, clinical#39, clinical#29  
**Deferred:** engine#387 (dynamic candidateGroups in YAML humanTask binding)

---

## Scope

Five issues, one branch. All are S/XS with a shared structural refactor. No new
foundation dependencies. No new persistence units. No breaking changes to REST APIs.

---

## Issue #28 — CaseLifecycleEvent import fix

`CaseLifecycleEvent` was promoted from `io.casehub.engine.internal.event` to
`io.casehub.engine.common.spi.event` in engine#378. Two files import the old path:

| File | Old import | New import |
|------|-----------|-----------|
| `AeEscalationListener` | `io.casehub.engine.internal.event.CaseLifecycleEvent` | `io.casehub.engine.common.spi.event.CaseLifecycleEvent` |
| `AeEscalationListenerTest` | same + `io.casehub.engine.internal.model.CaseInstance` | `io.casehub.engine.common.internal.model.CaseInstance` |

`CaseInstance` moved to `io.casehub.engine.common.internal.model` — still internal,
in the `common` module. The test mocks it, so no constructor arity concern there.

**CaseLifecycleEvent arity change:** the new record has 7 components (adds `traceId`):

```java
public record CaseLifecycleEvent(
    UUID caseId, String commandType, String eventType,
    String caseStatus, String actorId, String actorRole, String traceId)
```

`AeEscalationListenerTest` constructs it with 6 args — update to 7, passing `null`
as `traceId` for all test invocations:

```java
new CaseLifecycleEvent(caseId, "CompleteCase", "CaseCompleted", "COMPLETED", "system", "system", null)
```

**Build guard:** run `mvn clean test-compile -pl runtime` (not `mvn test-compile`).
GE-20260526-43a51d: Maven incremental compile silently passes on record arity mismatches.
The clean compile catches the 6→7 arg error before running tests.

---

## Issue #39 — LAYER-LOG vertical slice build note

Add one paragraph to `LAYER-LOG.md` immediately after "Entries are ordered for
learning, not chronology":

```
**Build approach:** Layer ordering here is for teaching, not building. The recommended
pattern is vertical slice first — the thinnest working path through all layers — then
deepen each layer to production completeness. See `../parent/docs/AGENTIC-HARNESS-GUIDE.md`
§Build Order. Any layers built before this guidance existed are accurate history; future
layer work follows vertical slice first.
```

---

## Issues #27 + #26 — Shared three-phase refactor

### Background

`AeEscalationCaseService.onAdverseEventReported()` currently calls
`caseHub.startCase()` fire-and-forget inside `@Transactional`. Capturing the case ID
(#26) requires `.join()`, which requires splitting the transaction — same three-phase
pattern as `TrialActivationService`. Issue #27 (escalation status write-back) plugs
into Phase 1 and the existing `AeEscalationListener`, sharing the same refactor.

`IrbDeviationCaseService.onDeviationResolved()` is in the same position and gets the
same treatment for `ProtocolDeviation.engineCaseId` (#26).

### AeEscalationStatus enum

New enum `AeEscalationStatus` in `api/src/main/java/io/casehub/clinical/api/model/`:

| Value | Set by | When |
|-------|--------|------|
| `NONE` | (default) | Grade 1/2 AEs — no escalation initiated |
| `REQUESTED` | Phase 1 of `AeEscalationCaseService` | Grade 3+, case start initiated |
| `COMPLETED` | `AeEscalationListener.onCaseLifecycle()` | Case reached `CaseCompleted` |
| `FAILED` | Phase 2 catch block in `AeEscalationCaseService` | `startCase().join()` threw |

### Entity changes

| Entity | Field | Java type | JPA annotation | DB column |
|--------|-------|-----------|---------------|-----------|
| `AdverseEvent` | `escalationStatus` | `AeEscalationStatus` | `@Enumerated(STRING) @Column(name="escalation_status", nullable=false)` | `escalation_status VARCHAR(50) NOT NULL DEFAULT 'NONE'` |
| `AdverseEvent` | `engineCaseId` | `UUID` | `@Column(name="engine_case_id")` | `engine_case_id UUID` |
| `ProtocolDeviation` | `engineCaseId` | `UUID` | `@Column(name="engine_case_id")` | `engine_case_id UUID` |

### AeEscalationCaseService — three-phase + FAILED handling

```
onAdverseEventReported(@ObservesAsync event)   // no @Transactional on observer
  try:
    → Phase 1 @Transactional: prepareAndMarkRequested(event)
        look up AdverseEvent by event.aeId()
        if null → log WARN, return (skip all phases — entity not found)
        set ae.escalationStatus = REQUESTED
        evaluate AdverseEventEscalationPolicy
        build and return initialContext

    → Phase 2 (no TX):
        UUID caseId = caseHub.startCase(ctx).toCompletableFuture().join()

    → Phase 3 @Transactional: persistCaseId(event.aeId(), caseId)
        look up AdverseEvent by aeId
        if null → log WARN, return
        set ae.engineCaseId = caseId

    → Grade 4+ signaling (unchanged, after Phase 3)

  catch (Exception e):
    → Phase 4 @Transactional: markFailed(event.aeId())
        look up AdverseEvent by aeId
        if null → log WARN, return
        set ae.escalationStatus = FAILED
        log ERROR with cause
```

**Null handling rationale:** `@ObservesAsync` observers run outside any caller context.
Throwing propagates to the async executor thread, not to a REST response. Log-and-skip
is correct here — unlike `TrialActivationService` which is a synchronous REST call.

### AeEscalationListener — COMPLETED write-back (#27)

`onCaseLifecycle()` resolves `aeId` as a UUID from the case context. The `COMPLETED`
write-back fires after `aeId` resolves successfully, **before** the `enrollmentId`
null check — so the status reflects whether the case ran to completion regardless of
context completeness:

```java
// After aeId is resolved as UUID:
AdverseEvent ae = AdverseEvent.findById(aeId);
if (ae != null) ae.escalationStatus = AeEscalationStatus.COMPLETED;

// Then the existing enrollmentId check (unchanged):
if (enrollmentId == null) {
    LOG.warnf("...enrollmentId missing... ledger write skipped");
    return;
}
// ledger write + event fire continue as before
```

`AdverseEvent` is a Panache entity in `runtime` — accessible directly. Method is
already `@Transactional` — no structural change.

### IrbDeviationCaseService — three-phase structure (#26 + #29)

```
onDeviationResolved(@ObservesAsync event)   // remove @Transactional from observer

  try:
    → Phase 1 @Transactional: prepareAndCreateApproval(event)
        look up TrialSite by event.siteId()
        trialId = site != null ? site.trialId : null  (null is allowed)
        evaluate IrbCommitteeAssignmentPolicy (see #29)
        create IrbApproval with assignment.committeeId()
        build and return initialContext (includes committeeId, candidateGroups)

    → Phase 2 (no TX):
        UUID caseId = caseHub.startCase(ctx).toCompletableFuture().join()

    → Phase 3 @Transactional: persistDeviationCaseId(event.deviationId(), caseId)
        look up ProtocolDeviation by deviationId
        if null → log WARN, return
        set deviation.engineCaseId = caseId

  catch (Exception e):
    log ERROR with cause (no FAILED state — ProtocolDeviation has no escalationStatus)
```

**TrialSite null handling:** `trialId` is optional context for the SPI (enables
per-trial committee routing). If `TrialSite` doesn't exist, pass `null` — the default
policy ignores it. Don't abort.

### Flyway migrations (default datasource, db/migration/default/)

| Version | Statement |
|---------|-----------|
| V111 | `ALTER TABLE adverse_event ADD COLUMN escalation_status VARCHAR(50) NOT NULL DEFAULT 'NONE'` |
| V112 | `ALTER TABLE adverse_event ADD COLUMN engine_case_id UUID` |
| V113 | `ALTER TABLE protocol_deviation ADD COLUMN engine_case_id UUID` |

No data backfill needed — existing AEs correctly default to NONE; existing deviations
have null engine_case_id.

---

## Issue #29 — IrbCommitteeAssignmentPolicy SPI

### New types in `api/src/main/java/io/casehub/clinical/api/spi/`

```java
// Context passed to the policy — escalationRequirement omitted (always IRB_REVIEW at call site)
public record IrbCommitteeContext(
    UUID deviationId,
    UUID siteId,
    UUID trialId,        // resolved from TrialSite in Phase 1; may be null
    DeviationSeverity severity)

// Assignment returned by the policy
public record IrbCommitteeAssignment(String committeeId, List<String> candidateGroups)

// The SPI — evaluate() matches AdverseEventEscalationPolicy and DeviationResponsePolicy convention
@FunctionalInterface
public interface IrbCommitteeAssignmentPolicy {
    IrbCommitteeAssignment evaluate(IrbCommitteeContext context);
}
```

### DefaultIrbCommitteeAssignmentPolicy in `runtime/src/main/java/io/casehub/clinical/service/`

```java
@ApplicationScoped
@DefaultBean
public class DefaultIrbCommitteeAssignmentPolicy implements IrbCommitteeAssignmentPolicy {
    public IrbCommitteeAssignment evaluate(IrbCommitteeContext context) {
        return new IrbCommitteeAssignment("irb-committee", List.of("irb-committee"));
    }
}
```

### IrbDeviationCaseService Phase 1 integration

- `approval.committeeId = assignment.committeeId()`
- `initialContext` gets `"committeeId"` and `"candidateGroups"` keys

The YAML `candidateGroups: [irb-committee]` remains hardcoded until engine#387
(dynamic candidateGroups from case context) is resolved. The SPI fully controls the
domain entity side (`IrbApproval.committeeId`) today.

---

## Tests

All new assertions fold into the existing `@QuarkusTest` lifecycle tests. No new
Mockito-only test classes — `AeEscalationCaseService` and `IrbDeviationCaseService`
call Panache Active Record methods (static) which require a Quarkus context.

### Prerequisite entity creation (fixes the integration test breakage)

Both lifecycle tests must grow `@BeforeEach @Transactional` entity setup, because
Phase 1 now looks up real entities by the random UUIDs the tests already use.

**`AeEscalationLifecycleTest` — add `@Transactional` to `setup()` and persist:**
```java
@BeforeEach
@Transactional
void setup() {
    aeId = UUID.randomUUID();
    enrollmentId = UUID.randomUUID();
    siteId = UUID.randomUUID();
    completionCapture.reset();

    AdverseEvent ae = new AdverseEvent();
    ae.id = aeId;
    ae.enrollmentId = enrollmentId;
    ae.grade = CtcaeGrade.GRADE_3;          // overridden per-test where needed
    ae.actuality = EventActuality.ACTUAL;
    ae.outcome = AeOutcome.ONGOING;
    ae.occurredAt = Instant.now();
    ae.reportedAt = Instant.now();
    ae.persist();
}
```

**`IrbGateLifecycleTest` — add `@Transactional` to `setup()` and persist:**
```java
@BeforeEach
@Transactional
void setup() {
    deviationId = UUID.randomUUID();
    siteId = UUID.randomUUID();
    UUID trialId = UUID.randomUUID();
    completionCapture.reset();

    TrialSite site = new TrialSite();
    site.id = siteId;
    site.trialId = trialId;
    site.investigatorId = "test-pi";
    site.persist();

    ProtocolDeviation deviation = new ProtocolDeviation();
    deviation.id = deviationId;
    deviation.siteId = siteId;
    deviation.deviationType = "CONSENT_DEVIATION";
    deviation.severity = DeviationSeverity.CRITICAL;
    deviation.piApprovalStatus = PiApprovalStatus.APPROVED;
    deviation.persist();
}
```

**`IrbGateLifecycleTest` comment:** update the stale comment on checkpoint 1 —
`onDeviationResolved` is no longer `@Transactional`, so the `(@Transactional honoured)`
note is wrong. Replace with a note that the observer now delegates to internal
`@Transactional` phase methods.

### Assertions to add to lifecycle tests

| Test class | New assertion method | What it verifies |
|-----------|---------------------|-----------------|
| `AeEscalationLifecycleTest` | `grade3_sets_escalation_status_REQUESTED_then_COMPLETED` | REQUESTED after `onAdverseEventReported`; COMPLETED after case closes |
| `AeEscalationLifecycleTest` | (extend existing) | `ae.engineCaseId` non-null after case start |
| `IrbGateLifecycleTest` | `irb_approved_persists_engine_case_id_on_deviation` | `deviation.engineCaseId` non-null after case start |
| `IrbGateLifecycleTest` | `irb_uses_policy_for_committee_id` | inject `@Alternative` policy, verify `approval.committeeId` reflects it |

### Other test additions

| Test class | What | Notes |
|-----------|------|-------|
| `DefaultIrbCommitteeAssignmentPolicyTest` | Pure unit: `evaluate()` returns non-null, non-empty `committeeId` and `candidateGroups` | Plain JUnit, no Quarkus |
| `AeEscalationListenerTest` | Import fix + `traceId` arg on constructor calls | Existing tests; re-verify with `mvn clean test-compile` |

---

## Architectural debt note

`TrialActivationService`, `AeEscalationCaseService`, and `IrbDeviationCaseService`
now all implement the three-phase pattern. A `ThreePhaseStartCase` utility is worth
extracting at the fourth use. No action this branch — but the fourth copy-paste should
trigger a refactor issue rather than a fourth duplicate.

---

## Deferred

- **engine#387** — dynamic `candidateGroups` in YAML humanTask binding. Blocks full
  SPI control of WorkItem routing for #29. Domain side is already SPI-controlled.
