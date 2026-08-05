# io.casehub.clinical.api.DsmbSafetySignalEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

## Fields

### `affectedSites` (`java.util.List<java.util.UUID>`)

### `signalType` (`java.lang.String`)

### `summary` (`java.lang.String`)

### `tenantId` (`java.lang.String`)

### `trialId` (`java.util.UUID`)

## Record Components

### `affectedSites` (`java.util.List<java.util.UUID>`)

### `signalType` (`java.lang.String`)

### `summary` (`java.lang.String`)

### `tenantId` (`java.lang.String`)

### `trialId` (`java.util.UUID`)

## Constructors

### `public DsmbSafetySignalEvent(java.util.UUID trialId, java.lang.String signalType, java.util.List<java.util.UUID> affectedSites, java.lang.String summary, java.lang.String tenantId)`

#### Parameters

- `trialId` (`java.util.UUID`)
- `signalType` (`java.lang.String`)
- `affectedSites` (`java.util.List<java.util.UUID>`)
- `summary` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

## Methods

### `public java.util.List<java.util.UUID> affectedSites()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String signalType()`

### `public java.lang.String summary()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`

### `public java.util.UUID trialId()`
