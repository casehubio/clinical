# io.casehub.clinical.api.SiteEnrollmentAlertEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

## Fields

### `matchCount` (`int`)

### `predictedOutcome` (`java.lang.String`)

### `predictedProbability` (`double`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

### `topScore` (`double`)

### `traceId` (`java.lang.String`)

### `trialId` (`java.util.UUID`)

## Record Components

### `matchCount` (`int`)

### `predictedOutcome` (`java.lang.String`)

### `predictedProbability` (`double`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

### `topScore` (`double`)

### `traceId` (`java.lang.String`)

### `trialId` (`java.util.UUID`)

## Constructors

### `public SiteEnrollmentAlertEvent(java.util.UUID siteId, java.util.UUID trialId, int matchCount, double topScore, java.lang.String predictedOutcome, double predictedProbability, java.lang.String traceId, java.lang.String tenantId)`

#### Parameters

- `siteId` (`java.util.UUID`)
- `trialId` (`java.util.UUID`)
- `matchCount` (`int`)
- `topScore` (`double`)
- `predictedOutcome` (`java.lang.String`)
- `predictedProbability` (`double`)
- `traceId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

## Methods

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

### `public java.util.UUID trialId()`
