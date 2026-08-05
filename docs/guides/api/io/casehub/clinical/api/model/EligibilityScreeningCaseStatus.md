# io.casehub.clinical.api.model.EligibilityScreeningCaseStatus

**Package:** `io.casehub.clinical.api.model`

**Kind:** `enum`

Engine case lifecycle status — same shape as SusarOversightStatus / AeEscalationStatus.

## Enum Constants

### `COMPLETED` (`io.casehub.clinical.api.model.EligibilityScreeningCaseStatus`)

Engine case goal reached — IRB consultation completed or criteria assessed.

### `FAILED` (`io.casehub.clinical.api.model.EligibilityScreeningCaseStatus`)

Engine case start failed; eligibility screening case status stuck.

### `NONE` (`io.casehub.clinical.api.model.EligibilityScreeningCaseStatus`)

No eligibility screening case started — initial state.

### `REQUESTED` (`io.casehub.clinical.api.model.EligibilityScreeningCaseStatus`)

Eligibility screening case start requested; engine case being initialised.

## Constructors

### `private EligibilityScreeningCaseStatus()`

## Methods

### `public static io.casehub.clinical.api.model.EligibilityScreeningCaseStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.clinical.api.model.EligibilityScreeningCaseStatus[] values()`
