# io.casehub.clinical.api.AeTrajectoryAlertEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

## Fields

### `aeId` (`java.util.UUID`)

### `currentGrade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `enrollmentId` (`java.util.UUID`)

### `matchCount` (`int`)

### `predictedOutcome` (`java.lang.String`)

### `predictedProbability` (`double`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

### `topScore` (`double`)

### `traceId` (`java.lang.String`)

## Record Components

### `aeId` (`java.util.UUID`)

### `currentGrade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `enrollmentId` (`java.util.UUID`)

### `matchCount` (`int`)

### `predictedOutcome` (`java.lang.String`)

### `predictedProbability` (`double`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

### `topScore` (`double`)

### `traceId` (`java.lang.String`)

## Constructors

### `public AeTrajectoryAlertEvent(java.util.UUID aeId, java.util.UUID enrollmentId, java.util.UUID siteId, io.casehub.clinical.api.model.CtcaeGrade currentGrade, int matchCount, double topScore, java.lang.String predictedOutcome, double predictedProbability, java.lang.String traceId, java.lang.String tenantId)`

#### Parameters

- `aeId` (`java.util.UUID`)
- `enrollmentId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `currentGrade` (`io.casehub.clinical.api.model.CtcaeGrade`)
- `matchCount` (`int`)
- `topScore` (`double`)
- `predictedOutcome` (`java.lang.String`)
- `predictedProbability` (`double`)
- `traceId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

## Methods

### `public java.util.UUID aeId()`

### `public io.casehub.clinical.api.model.CtcaeGrade currentGrade()`

### `public java.util.UUID enrollmentId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int matchCount()`

### `public java.lang.String predictedOutcome()`

### `public double predictedProbability()`

### `public java.util.UUID siteId()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`

### `public double topScore()`

### `public java.lang.String traceId()`
