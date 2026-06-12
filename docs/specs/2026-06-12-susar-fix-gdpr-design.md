# SUSAR Fix and GDPR Compliance Design
**Issues:** #77 (SUSAR dedicated oversight case hub), #76 (SUSAR gate decision listener), #7 (GDPR and regulatory compliance)
**Branch:** `issue-76-susar-fix-gdpr`
**Date:** 2026-06-12 (revised 2026-06-13)

---

## Context

### #77: Why the Programmatic Binding Fails

The previous Layer 8 implementation added `SusarCriteriaEvaluator` as a programmatic worker binding to `ClinicalAdverseEventCaseHub.getDefinition()`. This fails because:

- The engine fires the first `CaseContextChangedEvent` before the initial context is applied to the live `CaseInstance`.
- `WorkerScheduleEventHandler` reads `instance.getCaseContext()` LIVE (not from the event snapshot), which is empty at the time of the first event.
- The binding filter `.susarAssessmentComplete == null` evaluates `null == null = true` against the empty context and fires immediately.
- `PlanningStrategyLoopControl` marks the plan item RUNNING after first dispatch, permanently blocking re-dispatch.

The fix: move SUSAR oversight to a dedicated case hub started only when criteria are confirmed, using a binding filter pattern that is false on the empty first event.

### #76: The Regulatory Audit Gap

When a SUSAR gate WorkItem is rejected or expires, the engine fires `ActionGateRejectedEvent` / `ActionGateExpiredEvent` on the Vert.x event bus. Clinical has no consumer. The result: `susarAssessmentComplete` is never written, the oversight case may complete silently without a gate decision record, and the FDA audit trail cannot distinguish "gate pending" from "clinician explicitly declined escalation".

### #7: GDPR Scope

No production deployment; no data migration concerns. All changes are code changes only.

---

## Issue #77: SUSAR Dedicated Oversight Case Hub

### New Files

**`runtime/src/main/resources/clinical/susar-oversight.yaml`**

```yaml
dsl: "0.1"
version: "1.0.0"
name: susar-oversight
namespace: clinical
title: SUSAR Expedited Safety Report Oversight Gate

spec:

  capabilities:
    - name: safety-monitoring
      inputSchema: "{ aeId: .aeId }"

  goals:
    - name: susar-complete
      kind: success
      condition: ".susarAssessmentComplete != null"

  bindings:
    - name: susar-assessment
      on:
        contextChange:
          filter: ".aeId != null and .susarAssessmentComplete == null"
      capability: safety-monitoring
```

**YAML field notes:**
- `` `inputSchema` is on the top-level `spec.capabilities` entry (not on the binding) — evaluated as JQ against the case context to produce the worker's input map. ``
- `` `capability: safety-monitoring` is a direct field on the binding (not nested under `worker:`) — references the named capability from `spec.capabilities`. ``
- `.aeId != null` guard — false on the empty first-context event, true on the second event when the full initial context is applied. Matches the established pattern in `ae-escalation.yaml`'s humanTask bindings. The case is only started when SUSAR criteria are pre-confirmed by the service layer, so the worker always returns a `PlannedAction` on valid execution.

**`ClinicalSusarOversightCaseHub`** (`runtime/src/main/java/io/casehub/clinical/service/`)

```java
@ApplicationScoped
public class ClinicalSusarOversightCaseHub extends YamlCaseHub {

    @Inject SusarEvaluatorFunction susarEvaluator;
    private volatile CaseDefinition augmentedDefinition;

    public ClinicalSusarOversightCaseHub() { super("clinical/susar-oversight.yaml"); }

    @Override
    public CaseDefinition getDefinition() {
        if (augmentedDefinition == null) {
            synchronized (this) {
                if (augmentedDefinition == null) {
                    CaseDefinition def = super.getDefinition();
                    def.getWorkers().add(Worker.builder()
                            .name("susar-criteria-evaluator")
                            .capabilities(List.of(Capability.builder()
                                    .name("safety-monitoring")
                                    .inputSchema("{ aeId: .aeId }")
                                    .outputSchema(".")
                                    .build()))
                            .function(susarEvaluator)
                            .build());
                    augmentedDefinition = def;
                }
            }
        }
        return augmentedDefinition;
    }
}
```

Without `.function(susarEvaluator)`, the engine has no Java function to dispatch to when the `safety-monitoring` capability is scheduled — the `spec.capabilities` entry connects the YAML binding to this programmatic worker registration.

**`SusarOversightCaseService`** (`runtime/src/main/java/io/casehub/clinical/service/`)

Observes `AdverseEventReportedEvent` concurrently with `AeEscalationCaseService`. Three-phase pattern (same as `TrialActivationService`):

- Phase 1 `@Transactional`: load `AdverseEvent` from DB, call `SusarCriteriaEvaluator.apply(context)` directly as a CDI bean. **Idempotency guard:** if `ae.susarOversightStatus != NONE`, return early (concurrent delivery or re-delivery protection). If `susarRequired=false`, return early — no oversight case started. Otherwise set `ae.susarOversightStatus = REQUESTED` and persist.
- Phase 2 (no TX): `susarOversightCaseHub.startCase(initialContext).toCompletableFuture().join()`
- Phase 3 `@Transactional`: `ae.susarOversightCaseId = caseId; ae.persist()`
- On any exception from Phase 2/3: `markFailed(aeId)` sets `ae.susarOversightStatus = FAILED`

Initial context passed to the oversight case: `{aeId, grade, unexpected, suspected, enrollmentId, siteId, tenantId}`.

### New: `SusarOversightStatus` enum (`api/src/main/java/io/casehub/clinical/api/model/`)

```java
public enum SusarOversightStatus {
    /** Default — Grade 1/2 or criteria not met; no oversight case. */
    NONE,
    /** SUSAR criteria confirmed; oversight case start requested. */
    REQUESTED,
    /** Oversight case goal satisfied (gate approved or rejected). */
    COMPLETED,
    /** Case start failed — engine unavailable or pool timeout. */
    FAILED
}
```

### Modified Files

**`AdverseEvent` entity** — add:
- `susarOversightStatus SusarOversightStatus` field (default `NONE`)
- `susarOversightCaseId UUID` field

**`V119__ae_susar_oversight_fields.sql`** (default datasource):
```sql
ALTER TABLE adverse_event ADD COLUMN susar_oversight_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE adverse_event ADD COLUMN susar_oversight_case_id UUID;
```

### ae-escalation.yaml — No Changes

The SUSAR worker binding that was reverted is not re-added. The ae-escalation case retains its `safety-review` and `dsmb-escalation` bindings unchanged.

---

## Issue #76: SUSAR Gate Decision Listener

### Event Bus Addresses (from `ActionGateCompletionApplier`)

| Address | Event record | When fired |
|---|---|---|
| `"casehub.action.gate.approved"` | `ActionGateApprovedEvent(caseId, gateId, workItemResolution, approvedBy)` | WorkItem COMPLETED with approval |
| `"casehub.action.gate.rejected"` | `ActionGateRejectedEvent(caseId, gateId, workItemResolution, rejectedBy)` | WorkItem REJECTED or CANCELLED |
| `"casehub.action.gate.expired"` | `ActionGateExpiredEvent(caseId, gateId)` | WorkItem EXPIRED |

### Gate Discrimination — DB Query, Not Cache

The engine's `ActionGateRejectedHandler`, `ActionGateApprovedHandler`, and `ActionGateExpiredHandler` all subscribe to the same Vert.x event bus addresses as clinical's listener. They are competing `@ConsumeEvent` consumers. All three handlers call `instance.setPendingActionGate(null)` as their first action — before publishing downstream events. Execution order against clinical's consumer is non-deterministic on the Vert.x worker thread pool.

**Do NOT use `CaseInstanceCache.get(caseId).getPendingActionGate()` for discrimination** — if the engine's handler runs first, `pendingActionGate` is null by the time clinical reads it. The spec originally proposed this approach; it is a confirmed race condition that silently drops the ledger write with no error.

**Correct approach:** DB query. `SusarOversightCaseService.Phase 3` persists `ae.susarOversightCaseId = caseId`. The listener queries:

```java
AdverseEvent ae = AdverseEvent.findBySusarOversightCaseId(event.caseId());
if (ae == null) return; // not a SUSAR oversight case gate
```

This is race-free (DB write happens before gate WorkItem creation), restart-safe (survives JVM restart unlike `CaseInstanceCache`), and also provides `aeId`, `enrollmentId`, and `grade` without any additional lookups.

Add named query `AdverseEvent.findBySusarOversightCaseId(UUID caseId)` to the entity (plain `findById`-style query, no tenant scope — caseId is globally unique).

### New: `SusarDecisionLedgerEntry` (`runtime/src/main/java/io/casehub/clinical/ledger/`)

JOINED inheritance on qhorus datasource. V2021 migration.

Fields:
- `aeId UUID` — the adverse event (domain reference, not subject scope)
- `enrollmentId UUID` — the enrolled patient
- `ctcaeGrade String` — CTCAE grade at time of decision
- `gateOutcome String` — `"APPROVED"`, `"REJECTED"`, or `"EXPIRED"`
- `decidedAt Instant`
- `decidedBy String` — clinician actorId (null for EXPIRED)

`subjectId = enrollmentId` (NOT `aeId`) — `LedgerProvExportService.exportSubject(enrollmentId, tenancyId)` retrieves entries by `subjectId`. Setting `subjectId = aeId` would make SUSAR gate decisions invisible in the patient's PROV export, which is a compliance gap for FDA submission.

`actorId = decidedBy` for APPROVED/REJECTED; `ClinicalActors.CLINICAL_SERVICE` for EXPIRED.

`domainContentBytes()`: `aeId | enrollmentId | ctcaeGrade | gateOutcome | decidedAt`. UUID-only fields — no named PII. Merkle chain survives consent withdrawal erasure.

Carries a `ComplianceSupplement` (EU AI Act Art.12) — see §ComplianceSupplement.

**`V2021__susar_decision_ledger_entry.sql`** (qhorus datasource):
```sql
CREATE TABLE susar_decision_ledger_entry (
    id UUID PRIMARY KEY,
    ae_id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    ctcae_grade VARCHAR(20),
    gate_outcome VARCHAR(20) NOT NULL,
    decided_at TIMESTAMP NOT NULL,
    decided_by VARCHAR(255),
    CONSTRAINT fk_susar_decision_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
```

### New: `SusarDecisionLedgerWriter` (`runtime/src/main/java/io/casehub/clinical/service/`)

`@ApplicationScoped`. Writes all three outcomes in `@Transactional(REQUIRES_NEW)` — commits independently of any surrounding transaction state.

### New: `SusarGateDecisionListener` (`runtime/src/main/java/io/casehub/clinical/service/`)

`@ApplicationScoped`. Three `@ConsumeEvent(blocking = true)` methods, one per address. All use the DB discriminator pattern.

**Approved path:** write ledger entry with `gateOutcome = APPROVED`. No case signalling — the engine's `ActionGateApprovedHandler` calls `refireCompletion()` which re-fires `WorkflowExecutionCompleted(plannedAction=null)` with the deferred output. `WorkflowExecutionCompletedHandler` applies the output to context (`susarRequired: true, susarAssessmentComplete: true`), satisfying the `susar-complete` goal.

**Rejected and expired paths:** signal oversight case + write ledger entry:
```java
susarOversightCaseHub.signal(event.caseId(), "susarAssessmentComplete", true);
susarOversightCaseHub.signal(event.caseId(), "susarRequired", false);
ledgerWriter.writeEntry(ae, "REJECTED"/"EXPIRED", decidedAt, decidedBy);
```

This closes the `susarAssessmentComplete != null` goal, producing a complete case lifecycle with full audit trail for all outcomes.

---

## Issue #7: GDPR and Regulatory Compliance

### 1. GDPR Art.17 Consent Withdrawal

**`ConsentWithdrawalService`** (`runtime/src/main/java/io/casehub/clinical/service/`)

`POST /trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/withdraw-consent` (added to `PatientResource`)

**XA required:** This service writes to both the default datasource (`PatientEnrollment`) and qhorus datasource (`ConsentWithdrawalLedgerEntry`) in the same `@Transactional` boundary. `quarkus.datasource.jdbc.transactions=xa` and `quarkus.datasource.qhorus.jdbc.transactions=xa` must both be set (already configured for `ProtocolDeviationService`, `DeviationExpirer`, `AdverseEventService`). Without XA, Agroal throws "Failed to enlist" on every invocation with no helpful diagnostic.

Steps (all in one `@Transactional`):
1. Load `PatientEnrollment` by `enrollmentId` + tenant. 404 if not found.
2. Guard: if already `WITHDRAWN`, return 409.
3. `enrollment.consentStatus = ConsentStatus.WITHDRAWN`
4. `enrollment.enrollmentStatus = EnrollmentStatus.WITHDRAWN` (FHIR ResearchSubject.subjectState; GCP distinguishes withdrawn from screened-out or off-study)
5. `enrollment.withdrawnAt = Instant.now()`
6. `enrollment.patientId = "erased-" + UUID.randomUUID()` — pseudonymizes the external identifier. The `enrollment.id` UUID is retained for chain-of-custody integrity.
7. `enrollment.persist()`
8. Write `ConsentWithdrawalLedgerEntry` with `actorId = enrollmentId.toString()` (see below)
9. Capture `ErasureResult result = LedgerErasureService.erase(enrollmentId.toString())` — pseudonymizes `actorId` field in all ledger entries where `actorId = token_for(enrollmentId.toString())`. This includes the withdrawal entry just written, and any future entries written with the patient as actor.
10. Update `ConsentWithdrawalLedgerEntry.ledgerEntriesAffected = result.affectedEntryCount()` and re-persist (or use a builder pattern to write the final entry after calling erase)
11. Call `memoryStore.eraseEntity(enrollmentId, tenantId)` — wipes patient-specific memories keyed by `patient:{enrollmentId}` (GDPR Art.5(2))

**Note on step 9/10 ordering:** `LedgerErasureService.erase()` returns `ErasureResult(rawActorId, mappingFound, affectedEntryCount)`. The count must be captured and written to the withdrawal entry. The simplest pattern: write the entry twice — first without the count, then update after erase returns. Or: compute the count before writing and pass it in. Either is acceptable; use what avoids a second persist.

**Note on `casehub.ledger.identity.tokenisation.enabled=true`:** `LedgerErasureService.erase()` is a no-op unless this flag is set (GE-20260531-46f8ab). Without it, `InternalActorIdentityProvider` is not active — no `ActorIdentity` rows are created at persist time, so `erase()` always returns `mappingFound=false, affectedEntryCount=0`. Add to both `application.properties` (production) and test `application.properties`.

**`ConsentWithdrawalLedgerEntry extends LedgerEntry`** (qhorus datasource, V2022):
- `actorId = enrollmentId.toString()` — the patient is the actor of their own consent withdrawal. `LedgerErasureService.erase(enrollmentId.toString())` pseudonymizes this field post-erasure, so even the withdrawal record is de-identified.
- `subjectId = enrollmentId`
- Fields: `enrollmentId UUID`, `withdrawnAt Instant`, `ledgerEntriesAffected long` (populated from `ErasureResult.affectedEntryCount()`), `memoriesErased boolean`
- `domainContentBytes()`: UUID strings + withdrawnAt only — no named PII. Merkle chain survives erasure.

**`V120__patient_enrollment_withdrawn_at.sql`** (default datasource):
```sql
ALTER TABLE patient_enrollment ADD COLUMN withdrawn_at TIMESTAMP;
```

**`V2022__consent_withdrawal_ledger_entry.sql`** (qhorus datasource):
```sql
CREATE TABLE consent_withdrawal_ledger_entry (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL,
    withdrawn_at TIMESTAMP NOT NULL,
    ledger_entries_affected BIGINT NOT NULL DEFAULT 0,
    memories_erased BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_consent_withdrawal_ledger_entry_base FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
```

### 2. ComplianceSupplement — All AI Agent Decision Entries

**`ClinicalComplianceSupplement`** — factory class in `runtime/src/main/java/io/casehub/clinical/service/`. Centralizes supplement construction. No free-form strings in writers. Lives in `runtime/` not `api/spi/` — carries GCP reference strings, not consumed downstream.

```java
public final class ClinicalComplianceSupplement {
    public static ComplianceSupplement aeEscalation() { ... }
    public static ComplianceSupplement irbDecision() { ... }
    public static ComplianceSupplement protocolDeviation() { ... }
    public static ComplianceSupplement susarGateDecision() { ... }
    public static ComplianceSupplement safetyOfficerNotification() { ... }
    public static ComplianceSupplement sponsorNotification() { ... }
}
```

Each supplement carries:
- `planRef` — GCP/FDA reference (e.g., `"ICH E6(R3) §5.17 — serious adverse event reporting"`)
- `algorithmRef` — which algorithm/policy made the decision (e.g., `"AdverseEventEscalationPolicy (rule-based CTCAE routing)"`)
- `humanOverrideAvailable = true` — all clinical AI decisions are subject to PI override

All six `LedgerWriter` beans updated to call the appropriate factory method and attach the supplement to the primary decision entry.

### 3. W3C PROV-DM Export

`GET /trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/audit/prov`

Added to `PatientResource`. Verify tenant scope before delegating.

```java
try {
    String jsonLd = ledgerProvExportService.exportSubject(enrollmentId, principal.tenancyId());
    return Response.ok(jsonLd).type("application/ld+json").build();
} catch (IllegalArgumentException e) {
    return Response.status(Response.Status.NOT_FOUND).build();
}
```

`LedgerProvExportService.exportSubject()` throws `IllegalArgumentException` when no entries exist — there is no global `ExceptionMapper<IllegalArgumentException>`. Must catch explicitly.

### 4. Merkle Inclusion Proof

`GET /trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/audit/entries/{entryId}/proof`

Added to `PatientResource`.

```java
try {
    InclusionProof proof = ledgerVerificationService.inclusionProof(entryId, principal.tenancyId());
    return Response.ok(proof).build();
} catch (IllegalArgumentException e) {
    return Response.status(Response.Status.NOT_FOUND).build();
}
```

Same reason — `LedgerVerificationService.inclusionProof()` throws `IllegalArgumentException` for missing entries.

---

## Migrations Summary

| Migration | Datasource | What |
|---|---|---|
| `V119__ae_susar_oversight_fields.sql` | default | `adverse_event.susar_oversight_status VARCHAR(20) DEFAULT 'NONE'` + `susar_oversight_case_id UUID` |
| `V120__patient_enrollment_withdrawn_at.sql` | default | `patient_enrollment.withdrawn_at TIMESTAMP` |
| `V2021__susar_decision_ledger_entry.sql` | qhorus | SUSAR gate decision join table + FK |
| `V2022__consent_withdrawal_ledger_entry.sql` | qhorus | Consent withdrawal join table + FK |

---

## Testing Plan

### #77: SusarOversightCaseService
- **Unit:** criteria met → `susarOversightStatus = REQUESTED`, case started; criteria not met → early return, no case; `susarOversightStatus` already `REQUESTED` → idempotency guard fires, no second case
- **Integration (`@QuarkusTest`):** `AdverseEventReportedEvent` with Grade 4 + unexpected → oversight case starts, `susarOversightCaseId` persisted; Grade 2 → no case started
- **Lifecycle test:** oversight case completes → `SusarOversightListener` observes `CaseLifecycleEvent` (matching `GoalReached`) and calls `SusarOversightStatusUpdater.markCompleted(aeId)` in `REQUIRES_NEW` — same pattern as `AeStatusUpdater` / `AeEscalationListener`

### #76: SusarGateDecisionListener
- Direct listener method calls (pattern: `SafetyOfficerNotificationIntegrationTest`)
- **Approved:** `findBySusarOversightCaseId` finds the AE → ledger entry written with `gateOutcome = APPROVED`
- **Rejected:** ledger entry written + oversight case signalled with `susarAssessmentComplete: true`
- **Expired:** same as rejected path
- **Non-SUSAR gate:** `findBySusarOversightCaseId` returns null → silently ignored, no ledger write
- **Approved-path lifecycle test (`@QuarkusTest`):** Full AE → SUSAR criteria met → oversight case starts → gate WorkItem created → WorkItem completed (approved) → engine re-fires `WorkflowExecutionCompleted` → `susarAssessmentComplete: true` written to oversight case context → `susar-complete` goal satisfied → case reaches terminal state. Pattern: `IrbGateLifecycleTest` approved path.

### #7: GDPR
- **ConsentWithdrawalService:** enrollment → `WITHDRAWN` status on both `consentStatus` and `enrollmentStatus`, `patientId` pseudonymized, `withdrawnAt` set, `ConsentWithdrawalLedgerEntry` written with correct `ledgerEntriesAffected` count
- **Requires `casehub.ledger.identity.tokenisation.enabled=true`** in test properties (GE-20260531-46f8ab) — without it, `erase()` always returns 0
- **PROV-DM endpoint:** valid enrollmentId → 200 JSON-LD; no ledger entries → 404
- **Merkle proof endpoint:** valid entryId → 200 proof; unknown entryId → 404
- **ComplianceSupplement:** verify all six writers attach supplement to primary decision entry

---

## Out of Scope

- Grade 3 unexpected AE → 15-day expedited path (21 CFR 312.32(c)(1)(ii))
- SUSAR regulatory filing binding (`regulatory-submission` goal)
- `pendingActionGate` DB persistence (engine#433) — JVM-restart gate loss is a known v1 constraint; the DB discriminator (`findBySusarOversightCaseId`) resolves the race without requiring engine#433
- Full GDPR erasure of domain-column UUIDs (e.g., `enrollmentId` in `ae_ledger_entry`) — these are UUIDs, not named PII; the meaningful erasure is `patientId` in the domain entity and the actorId tokenization in ledger entries we control
- NLI-based SUSAR classifier (engine#472)
