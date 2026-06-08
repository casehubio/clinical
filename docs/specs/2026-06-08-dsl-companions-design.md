# Design Spec — Fluent Java DSL Companions for Clinical Case Definitions

**Issue:** casehubio/clinical#50  
**Branch:** issue-50-dsl-companions  
**Date:** 2026-06-08  
**Protocol:** PP-20260518-case-definition-layers

---

## Context

Protocol PP-20260518-case-definition-layers requires every YAML case definition to have a companion fluent Java DSL class that produces the same canonical `CaseDefinition`. Clinical has three YAML definitions — `deviation-review.yaml`, `ae-escalation.yaml`, `trial-coordination.yaml` — and no companions.

The YAML path (via `YamlCaseHub`) is and remains the production authoring path. ARC42STORIES.MD §Layer 5 records the rationale: YAML is human-readable and enables future runtime loading without recompilation. The companions fulfil the protocol's pairing rule and provide a programmatic construction path that enables pure-Java equivalence verification.

---

## Placement

Companions in production scope, equivalence test in test scope:

```
runtime/src/main/java/io/casehub/clinical/casedefinition/
    DeviationReviewCaseDefinition.java
    AeEscalationCaseDefinition.java
    TrialCoordinationCaseDefinition.java

runtime/src/test/java/io/casehub/clinical/casedefinition/
    ClinicalCaseDefinitionEquivalenceTest.java
```

**Why production scope:** The protocol states "YAML and the fluent Java DSL are two equal production-grade authoring paths." The reference implementation (`devtown/PrReviewCaseDefinition`) is `src/main/java`, `public final class`. Clinical has no architectural reason to deviate. A future deployment that wants programmatic definition construction (runtime parameterisation, extension) can use the companion without a module restructure. The "no current consumers" argument is ephemeral; the "equal authoring paths" principle is architectural.

---

## Class Structure

All three companions are `public final class` with a private constructor and a static `build()` factory — matching the devtown reference:

```java
public final class DeviationReviewCaseDefinition {
    private DeviationReviewCaseDefinition() {}

    public static CaseDefinition build() {
        // ... DSL construction
    }
}
```

### Construction Pattern — Dual Goal Registration

Goals must be registered in **two independent places**. `CaseDefinition.Builder.build()` populates `def.getGoals()` and `def.getCompletion()` via completely separate code paths — `setCompletion()` has no side effect on the goals list. The equivalence test verifies both paths independently.

The required pattern (matching the devtown reference and how the YAML mapper operates via its internal `goalMap`):

```java
var irbDecided = Goal.builder().name("irb-decided")...build();

// Registration 1: goals list — feeds def.getGoals()
// Registration 2: completion expression — feeds AllOfGoalExpression.getGoals()
return CaseDefinition.builder()
    .namespace("clinical")
    .goals(irbDecided)                                   // Registration 1
    .completion(GoalExpression.allOf(irbDecided))        // Registration 2
    .bindings(...)
    .build();
```

Using the same `Goal` instances in both places is correct and intentional. Registering goals only in the completion expression leaves `def.getGoals()` empty; registering only in `.goals(...)` leaves the completion expression unpopulated. Both cause half the equivalence test to silently pass while the other half fails.

---

## Expression Language

**Clinical companions use JQ strings copied verbatim from the YAML — no lambdas.**

This is a deliberate departure from the devtown companion, which uses `LambdaExpressionEvaluator` throughout. The reason: clinical companions are structural mirrors for the protocol's pairing rule. JQ strings are the invariant that makes a meaningful equivalence test possible — both the YAML mapper and the DSL companion produce `JQExpressionEvaluator` records wrapping the same string, which compare equal by value. Lambda predicates have no value equality and cannot be structurally compared.

The protocol states that lambda conditions are the DSL's additional capability beyond what YAML can express. That capability is out of scope for this issue and will be demonstrated separately.

| YAML field | DSL call | Note |
|---|---|---|
| `goal.condition` | `Goal.builder().condition(String)` | wraps `JQExpressionEvaluator` (record) |
| `on.contextChange.filter` | `new ContextChangeTrigger(String)` | wraps `JQExpressionEvaluator` (record) |
| `humanTask.title` | `HumanTaskTarget.inline().title(String)` | string |
| `humanTask.inputMapping` | `.inputMapping(String)` | wraps `JQExpressionEvaluator` (record) |
| `humanTask.outputMapping` | `.outputMapping(String)` | wraps `JQExpressionEvaluator` (record) |
| `humanTask.candidateGroups` | `.candidateGroups(Set.of(...))` | wraps `ListEvaluator.StaticList` (record) |
| `humanTask.expiresIn` | `.expiresIn(Duration.parse(...))` | `java.time.Duration` |

`JQExpressionEvaluator` and `ListEvaluator.StaticList` are both Java records — their `equals()` compares by field value. This is what makes direct `assertThat(dsl).isEqualTo(yaml)` assertions work on evaluators and list evaluators without casts or `.toString()`.

**The `dsl` field is not set in companions.** `CaseDefinition.Builder` has no `dsl()` method; `dsl` is a YAML-schema version identifier (`"0.1"` in all three clinical YAMLs) set post-build by `CaseDefinitionYamlMapper` via `setDsl()`. It has no meaning in the programmatic DSL path. The equivalence test explicitly excludes it.

---

## The Three Companions

All field values are copied verbatim from the YAML source.

### DeviationReviewCaseDefinition

Mirrors `clinical/deviation-review.yaml`:

| Field | Value |
|---|---|
| namespace | `clinical` |
| name | `deviation-review` |
| version | `1.0.0` |
| title | `Protocol Deviation Review — IRB consultation gate` |
| Goals | `irb-decided` (SUCCESS), condition `".irbConsultation != null"` |
| Completion | `allOf[irb-decided]` |
| Binding name | `irb-consultation` |
| Trigger filter | `".irbConsultationRequired == true and .irbConsultation == null"` |
| humanTask.title | `"IRB consultation required — protocol deviation"` |
| humanTask.expiresIn | `PT72H` |
| humanTask.candidateGroups | `[irb-committee]` |
| humanTask.inputMapping | `"{ deviationId: .deviationId, severity: .severity }"` |
| humanTask.outputMapping | `"{ irbConsultation: . }"` |

### AeEscalationCaseDefinition

Mirrors `clinical/ae-escalation.yaml`:

| Field | Value |
|---|---|
| namespace | `clinical` |
| name | `ae-escalation` |
| version | `1.0.0` |
| title | `Adverse Event Safety Escalation — adaptive severity routing` |
| Goal 1 | `safety-review-complete` (SUCCESS), condition `".safetyReview != null"` |
| Goal 2 | `dsmb-complete` (SUCCESS), condition `".requiresDsmbEscalation == false or .dsmbEscalation != null"` |
| Completion | `allOf[safety-review-complete, dsmb-complete]` |
| Binding 1 name | `safety-review` |
| Binding 1 trigger | `".requiresSeniorMonitor == true and .safetyReview == null"` |
| Binding 1 humanTask.title | `"Senior safety monitor review — adverse event"` |
| Binding 1 humanTask.expiresIn | `PT24H` |
| Binding 1 humanTask.candidateGroups | `[senior-safety-monitors]` |
| Binding 1 humanTask.inputMapping | `"{ aeId: .aeId, grade: .grade, enrollmentId: .enrollmentId }"` |
| Binding 1 humanTask.outputMapping | `"{ safetyReview: . }"` |
| Binding 2 name | `dsmb-escalation` |
| Binding 2 trigger | `".requiresDsmbEscalation == true and .dsmbEscalation == null"` |
| Binding 2 humanTask.title | `"DSMB escalation — Grade 4+ adverse event"` |
| Binding 2 humanTask.expiresIn | `PT24H` |
| Binding 2 humanTask.candidateGroups | `[dsmb]` |
| Binding 2 humanTask.inputMapping | `"{ aeId: .aeId, grade: .grade, enrollmentId: .enrollmentId }"` |
| Binding 2 humanTask.outputMapping | `"{ dsmbEscalation: . }"` |

### TrialCoordinationCaseDefinition

Mirrors `clinical/trial-coordination.yaml`:

| Field | Value |
|---|---|
| namespace | `clinical` |
| name | `trial-coordination` |
| version | `1.0.0` |
| title | `Clinical Trial Coordination — cross-site safety monitoring` |
| Goals | **none** — YAML has no `goals:` block; trial has no completion condition and runs for the trial lifetime |
| Completion | **null** — YAML has no `completion:` block |
| Binding name | `dsmb-rollup` |
| Trigger filter | `"[.grade4Active // {} | to_entries[] | select(.value == true)] | length >= 2"` |
| humanTask.title | `"DSMB review — simultaneous Grade 4+ events at multiple sites"` |
| humanTask.expiresIn | `PT48H` |
| humanTask.candidateGroups | `[dsmb]` |
| humanTask.inputMapping | `"{ trialId: .trialId, activeSites: [.grade4Active // {} | to_entries[] | select(.value == true) | .key] }"` |
| humanTask.outputMapping | `"{ dsmbReview: . }"` |

---

## Equivalence Test

`ClinicalCaseDefinitionEquivalenceTest` — plain JUnit 5, no Quarkus context, one `@Test` method per companion.

**Loading:** `CaseDefinitionYamlMapper.load(getClass().getClassLoader().getResourceAsStream("clinical/xxx.yaml"))` — confirmed from `PrReviewCaseDefinitionEquivalenceTest` in devtown.

**Why field-by-field:** `CaseDefinition.equals()` compares namespace + name + version only. `ContextChangeTrigger` and `HumanTaskTarget` are plain classes with no value equality. `usingRecursiveComparison()` without explicit type comparators would use reference equality for those types and always fail. Explicit field accessors are used throughout — matching the devtown reference pattern.

**Clinical's test is stronger than the devtown reference.** Devtown uses lambdas (no value equality for conditions), so it skips goal condition and trigger filter assertions. Clinical uses JQ strings (`JQExpressionEvaluator` records) throughout, so all evaluator fields can be compared by value.

**Assertion table:**

| What | How | Why it works |
|---|---|---|
| `namespace`, `name`, `version`, `title` | `isEqualTo(String)` | plain strings |
| Goals count | `hasSameSizeAs()` | |
| Goal name + kind | `isEqualTo()` per index | `Goal` field accessors |
| Goal condition | `assertThat(dslGoal.getCondition()).isEqualTo(yamlGoal.getCondition())` | `JQExpressionEvaluator` is a record; no cast needed |
| Bindings count | `hasSameSizeAs()` | |
| Binding name | `isEqualTo()` per index | |
| Binding target type | `isEqualTo(yamlBinding.target().getClass())` | all clinical bindings are `HumanTaskTarget` |
| Trigger filter | `assertThat(((ContextChangeTrigger) dslBinding.getOn()).getFilter()).isEqualTo(((ContextChangeTrigger) yamlBinding.getOn()).getFilter())` | `JQExpressionEvaluator` record equality |
| humanTask cast | `HumanTaskTarget dslHT = (HumanTaskTarget) dslBinding.target()` (and same for yaml) — or use pattern matching `instanceof HumanTaskTarget dslHT` after the target-type check above | `Binding.target()` returns `BindingTarget`; cast required before accessing humanTask fields |
| humanTask.title | `dslHT.title()` `isEqualTo` `yamlHT.title()` | string |
| humanTask.expiresIn | `dslHT.expiresIn()` `isEqualTo` `yamlHT.expiresIn()` | `Duration` value equality |
| humanTask.candidateGroups | `dslHT.candidateGroups()` `isEqualTo` `yamlHT.candidateGroups()` | `ListEvaluator.StaticList` is a record; `Set<String>` value equality |
| humanTask.inputMapping | `dslHT.inputMapping()` `isEqualTo` `yamlHT.inputMapping()` | `JQExpressionEvaluator` record equality |
| humanTask.outputMapping | `dslHT.outputMapping()` `isEqualTo` `yamlHT.outputMapping()` | `JQExpressionEvaluator` record equality |
| Completion type | `assertThat(dslDef.getCompletion()).isInstanceOf(GoalBasedCompletion.class)` | matches YAML-loaded type |
| Completion success type | `var dslCompletion = (GoalBasedCompletion) dslDef.getCompletion(); assertThat(dslCompletion.getSuccess()).isInstanceOf(AllOfGoalExpression.class)` | all three clinical YAMLs use `allOf`; the `isInstanceOf` check produces a meaningful failure message before the cast on the next row |
| Completion success goals | `assertThat(((AllOfGoalExpression) dslCompletion.getSuccess()).getGoals()).containsExactlyInAnyOrderElementsOf(((AllOfGoalExpression) ((GoalBasedCompletion) yamlDef.getCompletion()).getSuccess()).getGoals())` | `Goal.equals()` is fully value-based (name, condition, kind, terminal, description — all five fields); clinical YAMLs set none of terminal/description so both sides default-match; `containsExactlyInAnyOrderElementsOf` (not `containsExactlyInAnyOrder`) because the argument is a `Collection<Goal>`, not varargs |
| Completion failure | `assertThat(dslCompletion.getFailure()).isNull()` | clinical YAMLs have no `failure:` block; mapper produces `new GoalBasedCompletion(successExpr, null)` |
| `dsl` field | **not asserted** | `dsl` is a YAML-schema version set by the mapper; companions do not set it; excluding it is correct |
| `TrialCoordination` completion | `assertThat(dslDef.getCompletion()).isNull()` | no completion in YAML |
| `TrialCoordination` goals | `assertThat(dslDef.getGoals()).isEmpty()` | no goals in YAML |

---

## Out of Scope

- Lambda expression companions — the DSL's extended capability; demonstrated separately
- Replacing `YamlCaseHub` subclasses with DSL-backed production registration
- `dsl` field parity — YAML-schema version, not a programmatic DSL concern
