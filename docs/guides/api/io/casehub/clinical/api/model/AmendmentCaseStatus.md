# io.casehub.clinical.api.model.AmendmentCaseStatus

**Package:** `io.casehub.clinical.api.model`

**Kind:** `enum`

Engine case lifecycle status — same shape as AeEscalationStatus / SusarOversightStatus.

## Enum Constants

### `COMPLETED` (`io.casehub.clinical.api.model.AmendmentCaseStatus`)

Engine case goal reached — advisor recommendation applied.

### `FAILED` (`io.casehub.clinical.api.model.AmendmentCaseStatus`)

Engine case start failed; amendment stays at initial business status.

### `NONE` (`io.casehub.clinical.api.model.AmendmentCaseStatus`)

No amendment engine case started — initial state.

### `REQUESTED` (`io.casehub.clinical.api.model.AmendmentCaseStatus`)

Amendment engine case start requested; advisor being invoked.

## Constructors

### `private AmendmentCaseStatus()`

## Methods

### `public static io.casehub.clinical.api.model.AmendmentCaseStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.clinical.api.model.AmendmentCaseStatus[] values()`
