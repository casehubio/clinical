# io.casehub.clinical.api.model.ClinicalActionType

**Package:** `io.casehub.clinical.api.model`

**Kind:** `enum`

Typed taxonomy of consequential clinical trial agent actions requiring oversight gates.
All types are ALWAYS-gated — these are regulatory obligations, not configurable policy.

<p>candidateGroups semantics (GE-20260607-326c7e): fewer entries = more restrictive
in `ChainedReactiveActionRiskClassifier.narrower()`. SUSAR types (1 group) are the
tightest gates. Protocol deviation recording (2 groups) is broadest.

<p>Classification logic lives in `ClinicalActionRiskClassifier`. This enum owns
only the data — pure Java, no framework dependencies.

## Fields

### `OVERSIGHT_SCOPE` (`java.lang.String`)

### `candidateGroups` (`CandidateSetStrategy`)

### `reason` (`java.lang.String`)

### `reversible` (`boolean`)

## Enum Constants

### `DOSE_MODIFICATION` (`io.casehub.clinical.api.model.ClinicalActionType`)

### `PATIENT_WITHDRAWAL` (`io.casehub.clinical.api.model.ClinicalActionType`)

### `PROTOCOL_DEVIATION_RECORDING` (`io.casehub.clinical.api.model.ClinicalActionType`)

### `SUSAR_CRITERIA_DECISION` (`io.casehub.clinical.api.model.ClinicalActionType`)

### `SUSAR_REGULATORY_FILING` (`io.casehub.clinical.api.model.ClinicalActionType`)

## Constructors

### `private ClinicalActionType(boolean reversible, CandidateSetStrategy candidateGroups, java.lang.String reason)`

#### Parameters

- `reversible` (`boolean`)
- `candidateGroups` (`CandidateSetStrategy`)
- `reason` (`java.lang.String`)

## Methods

### `public java.lang.String actionType()`

Returns the PlannedAction actionType string: `SUSAR_CRITERIA_DECISION \u2192 "susar.criteria.decision"`.

### `public CandidateSetStrategy candidateGroups()`

### `public java.time.Duration expiresIn()`

Null — regulatory deadline policy is post-GA deployment config, not compile-time constant.

### `public static java.util.Optional<io.casehub.clinical.api.model.ClinicalActionType> fromActionType(java.lang.String actionType)`

Parses a `PlannedAction.actionType()` string back to the enum constant. Null-safe.

#### Parameters

- `actionType` (`java.lang.String`)

### `public java.lang.String reason()`

### `public java.lang.Class<?> resolutionType()`

Null — typed gate resolutions not yet defined for clinical action types.

### `public boolean reversible()`

### `public java.lang.String scope()`

### `public static io.casehub.clinical.api.model.ClinicalActionType valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.clinical.api.model.ClinicalActionType[] values()`
