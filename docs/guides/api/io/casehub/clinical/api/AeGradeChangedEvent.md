# io.casehub.clinical.api.AeGradeChangedEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

## Fields

### `aeId` (`java.util.UUID`)

### `changedAt` (`java.time.Instant`)

### `changedBy` (`java.lang.String`)

### `enrollmentId` (`java.util.UUID`)

### `newGrade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `previousGrade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

## Record Components

### `aeId` (`java.util.UUID`)

### `changedAt` (`java.time.Instant`)

### `changedBy` (`java.lang.String`)

### `enrollmentId` (`java.util.UUID`)

### `newGrade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `previousGrade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

## Constructors

### `public AeGradeChangedEvent(java.util.UUID aeId, java.util.UUID enrollmentId, java.util.UUID siteId, io.casehub.clinical.api.model.CtcaeGrade previousGrade, io.casehub.clinical.api.model.CtcaeGrade newGrade, java.time.Instant changedAt, java.lang.String changedBy, java.lang.String tenantId)`

#### Parameters

- `aeId` (`java.util.UUID`)
- `enrollmentId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `previousGrade` (`io.casehub.clinical.api.model.CtcaeGrade`)
- `newGrade` (`io.casehub.clinical.api.model.CtcaeGrade`)
- `changedAt` (`java.time.Instant`)
- `changedBy` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

## Methods

### `public java.util.UUID aeId()`

### `public java.time.Instant changedAt()`

### `public java.lang.String changedBy()`

### `public java.util.UUID enrollmentId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public boolean isDowngrade()`

### `public boolean isUpgrade()`

### `public io.casehub.clinical.api.model.CtcaeGrade newGrade()`

### `public io.casehub.clinical.api.model.CtcaeGrade previousGrade()`

### `public java.util.UUID siteId()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`
