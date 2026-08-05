# io.casehub.clinical.api.TrialStatusChangedEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

## Fields

### `newStatus` (`io.casehub.clinical.api.model.TrialStatus`)

### `oldStatus` (`io.casehub.clinical.api.model.TrialStatus`)

### `tenantId` (`java.lang.String`)

### `trialId` (`java.util.UUID`)

## Record Components

### `newStatus` (`io.casehub.clinical.api.model.TrialStatus`)

### `oldStatus` (`io.casehub.clinical.api.model.TrialStatus`)

### `tenantId` (`java.lang.String`)

### `trialId` (`java.util.UUID`)

## Constructors

### `public TrialStatusChangedEvent(java.util.UUID trialId, io.casehub.clinical.api.model.TrialStatus oldStatus, io.casehub.clinical.api.model.TrialStatus newStatus, java.lang.String tenantId)`

#### Parameters

- `trialId` (`java.util.UUID`)
- `oldStatus` (`io.casehub.clinical.api.model.TrialStatus`)
- `newStatus` (`io.casehub.clinical.api.model.TrialStatus`)
- `tenantId` (`java.lang.String`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.clinical.api.model.TrialStatus newStatus()`

### `public io.casehub.clinical.api.model.TrialStatus oldStatus()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`

### `public java.util.UUID trialId()`
