# io.casehub.clinical.api.spi.DeviationContext

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `record`

## Fields

### `deviationId` (`java.util.UUID`)

### `deviationType` (`java.lang.String`)

### `phase` (`io.casehub.clinical.api.model.TrialPhase`)

### `protocolId` (`java.lang.String`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `trialId` (`java.util.UUID`)

## Record Components

### `deviationId` (`java.util.UUID`)

### `deviationType` (`java.lang.String`)

### `phase` (`io.casehub.clinical.api.model.TrialPhase`)

### `protocolId` (`java.lang.String`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `trialId` (`java.util.UUID`)

## Constructors

### `public DeviationContext(java.util.UUID deviationId, java.util.UUID siteId, java.util.UUID trialId, java.lang.String protocolId, io.casehub.clinical.api.model.TrialPhase phase, io.casehub.clinical.api.model.DeviationSeverity severity, java.lang.String deviationType)`

#### Parameters

- `deviationId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `trialId` (`java.util.UUID`)
- `protocolId` (`java.lang.String`)
- `phase` (`io.casehub.clinical.api.model.TrialPhase`)
- `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)
- `deviationType` (`java.lang.String`)

## Methods

### `public java.util.UUID deviationId()`

### `public java.lang.String deviationType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.clinical.api.model.TrialPhase phase()`

### `public java.lang.String protocolId()`

### `public io.casehub.clinical.api.model.DeviationSeverity severity()`

### `public java.util.UUID siteId()`

### `public final java.lang.String toString()`

### `public java.util.UUID trialId()`
