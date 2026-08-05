# io.casehub.clinical.api.spi.IrbCommitteeContext

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `record`

Context passed to `IrbCommitteeAssignmentPolicy.evaluate`.
`trialId` may be null if the site has no active trial case.

## Fields

### `deviationId` (`java.util.UUID`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `trialId` (`java.util.UUID`)

## Record Components

### `deviationId` (`java.util.UUID`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `trialId` (`java.util.UUID`)

## Constructors

### `public IrbCommitteeContext(java.util.UUID deviationId, java.util.UUID siteId, java.util.UUID trialId, io.casehub.clinical.api.model.DeviationSeverity severity)`

#### Parameters

- `deviationId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `trialId` (`java.util.UUID`)
- `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

## Methods

### `public java.util.UUID deviationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.clinical.api.model.DeviationSeverity severity()`

### `public java.util.UUID siteId()`

### `public final java.lang.String toString()`

### `public java.util.UUID trialId()`
