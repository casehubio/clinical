# io.casehub.clinical.api.model.AeEscalationStatus

**Package:** `io.casehub.clinical.api.model`

**Kind:** `enum`

## Enum Constants

### `COMPLETED` (`io.casehub.clinical.api.model.AeEscalationStatus`)

Engine case reached CaseCompleted with safety review.

### `FAILED` (`io.casehub.clinical.api.model.AeEscalationStatus`)

Case start failed (engine unavailable, pool timeout).

### `NONE` (`io.casehub.clinical.api.model.AeEscalationStatus`)

Default — Grade 1/2 AEs; no escalation initiated.

### `REQUESTED` (`io.casehub.clinical.api.model.AeEscalationStatus`)

Grade 3+; escalation case started.

## Constructors

### `private AeEscalationStatus()`

## Methods

### `public static io.casehub.clinical.api.model.AeEscalationStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.clinical.api.model.AeEscalationStatus[] values()`
