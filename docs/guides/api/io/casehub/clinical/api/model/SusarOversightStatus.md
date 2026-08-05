# io.casehub.clinical.api.model.SusarOversightStatus

**Package:** `io.casehub.clinical.api.model`

**Kind:** `enum`

## Enum Constants

### `COMPLETED` (`io.casehub.clinical.api.model.SusarOversightStatus`)

Oversight case goal satisfied (gate approved or rejected).

### `FAILED` (`io.casehub.clinical.api.model.SusarOversightStatus`)

Case start failed — engine unavailable or pool timeout.

### `NONE` (`io.casehub.clinical.api.model.SusarOversightStatus`)

Default — Grade 1/2 or criteria not met; no oversight case.

### `REQUESTED` (`io.casehub.clinical.api.model.SusarOversightStatus`)

SUSAR criteria confirmed; oversight case start requested.

## Constructors

### `private SusarOversightStatus()`

## Methods

### `public static io.casehub.clinical.api.model.SusarOversightStatus valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.clinical.api.model.SusarOversightStatus[] values()`
