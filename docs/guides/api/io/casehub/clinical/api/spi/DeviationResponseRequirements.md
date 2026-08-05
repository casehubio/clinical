# io.casehub.clinical.api.spi.DeviationResponseRequirements

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `record`

## Fields

### `escalationRequirement` (`io.casehub.clinical.api.model.EscalationRequirement`)

### `piResponseDeadline` (`java.time.Duration`)

## Record Components

### `escalationRequirement` (`io.casehub.clinical.api.model.EscalationRequirement`)

### `piResponseDeadline` (`java.time.Duration`)

## Constructors

### `public DeviationResponseRequirements(java.time.Duration piResponseDeadline, io.casehub.clinical.api.model.EscalationRequirement escalationRequirement)`

#### Parameters

- `piResponseDeadline` (`java.time.Duration`)
- `escalationRequirement` (`io.casehub.clinical.api.model.EscalationRequirement`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.clinical.api.model.EscalationRequirement escalationRequirement()`

### `public final int hashCode()`

### `public java.time.Duration piResponseDeadline()`

### `public final java.lang.String toString()`
