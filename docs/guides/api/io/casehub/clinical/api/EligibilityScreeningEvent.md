# io.casehub.clinical.api.EligibilityScreeningEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

## Fields

### `criteriaResults` (`java.util.List<io.casehub.clinical.api.model.CriterionResult>`)

### `enrollmentId` (`java.util.UUID`)

### `screeningResult` (`io.casehub.clinical.api.model.EligibilityScreeningResult`)

### `tenantId` (`java.lang.String`)

## Record Components

### `criteriaResults` (`java.util.List<io.casehub.clinical.api.model.CriterionResult>`)

### `enrollmentId` (`java.util.UUID`)

### `screeningResult` (`io.casehub.clinical.api.model.EligibilityScreeningResult`)

### `tenantId` (`java.lang.String`)

## Constructors

### `public EligibilityScreeningEvent(java.util.UUID enrollmentId, java.lang.String tenantId, io.casehub.clinical.api.model.EligibilityScreeningResult screeningResult, java.util.List<io.casehub.clinical.api.model.CriterionResult> criteriaResults)`

#### Parameters

- `enrollmentId` (`java.util.UUID`)
- `tenantId` (`java.lang.String`)
- `screeningResult` (`io.casehub.clinical.api.model.EligibilityScreeningResult`)
- `criteriaResults` (`java.util.List<io.casehub.clinical.api.model.CriterionResult>`)

## Methods

### `public java.util.List<io.casehub.clinical.api.model.CriterionResult> criteriaResults()`

### `public java.util.UUID enrollmentId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.clinical.api.model.EligibilityScreeningResult screeningResult()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`
