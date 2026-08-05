# io.casehub.clinical.api.spi.AdverseEventContext

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `record`

Input context for `AdverseEventEscalationPolicy`. Carries all AE
identifiers and the CTCAE grade that determines escalation requirements.
Mirrors `DeviationContext` pattern.

## Fields

### `aeId` (`java.util.UUID`)

### `enrollmentId` (`java.util.UUID`)

### `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `siteId` (`java.util.UUID`)

## Record Components

### `aeId` (`java.util.UUID`)

### `enrollmentId` (`java.util.UUID`)

### `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `siteId` (`java.util.UUID`)

## Constructors

### `public AdverseEventContext(java.util.UUID aeId, java.util.UUID enrollmentId, java.util.UUID siteId, io.casehub.clinical.api.model.CtcaeGrade grade)`

#### Parameters

- `aeId` (`java.util.UUID`)
- `enrollmentId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)

## Methods

### `public java.util.UUID aeId()`

### `public java.util.UUID enrollmentId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.clinical.api.model.CtcaeGrade grade()`

### `public final int hashCode()`

### `public java.util.UUID siteId()`

### `public final java.lang.String toString()`
