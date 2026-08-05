# io.casehub.clinical.api.model.IrbDecision

**Package:** `io.casehub.clinical.api.model`

**Kind:** `enum`

IRB/ethics committee decision on a protocol deviation review or amendment.

## Enum Constants

### `APPROVED` (`io.casehub.clinical.api.model.IrbDecision`)

### `DEFERRED` (`io.casehub.clinical.api.model.IrbDecision`)

Committee requests additional information before deciding. Not a final rejection.

### `EXPIRED` (`io.casehub.clinical.api.model.IrbDecision`)

72-hour IRB WorkItem expired before committee decided.

### `PENDING` (`io.casehub.clinical.api.model.IrbDecision`)

### `REJECTED` (`io.casehub.clinical.api.model.IrbDecision`)

## Constructors

### `private IrbDecision()`

## Methods

### `public static io.casehub.clinical.api.model.IrbDecision valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.clinical.api.model.IrbDecision[] values()`
