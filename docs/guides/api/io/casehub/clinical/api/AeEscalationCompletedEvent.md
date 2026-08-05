# io.casehub.clinical.api.AeEscalationCompletedEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

CDI event fired when an AE escalation case completes — all required safety
reviews (senior monitor, and DSMB if Grade 4+) have been resolved.

<p>`unexpected` is derived from the case context at fire time (set by
AeEscalationCaseService from AdverseEvent.unexpected, added in Layer 8).
It is a material fact about the AE that belongs in the completion event for
downstream consumers.

## Fields

### `aeId` (`java.util.UUID`)

### `completedAt` (`java.time.Instant`)

### `dsmbEscalated` (`boolean`)

### `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `safetyReviewOutcome` (`java.lang.String`)

### `siteId` (`java.util.UUID`)

### `unexpected` (`boolean`)

## Record Components

### `aeId` (`java.util.UUID`)

### `completedAt` (`java.time.Instant`)

### `dsmbEscalated` (`boolean`)

### `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `safetyReviewOutcome` (`java.lang.String`)

### `siteId` (`java.util.UUID`)

### `unexpected` (`boolean`)

## Constructors

### `public AeEscalationCompletedEvent(java.util.UUID aeId, io.casehub.clinical.api.model.CtcaeGrade grade, java.util.UUID siteId, java.lang.String safetyReviewOutcome, boolean dsmbEscalated, java.time.Instant completedAt, boolean unexpected)`

#### Parameters

- `aeId` (`java.util.UUID`)
- `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)
- `siteId` (`java.util.UUID`)
- `safetyReviewOutcome` (`java.lang.String`)
- `dsmbEscalated` (`boolean`)
- `completedAt` (`java.time.Instant`)
- `unexpected` (`boolean`)

## Methods

### `public java.util.UUID aeId()`

### `public java.time.Instant completedAt()`

### `public boolean dsmbEscalated()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.clinical.api.model.CtcaeGrade grade()`

### `public final int hashCode()`

### `public java.lang.String safetyReviewOutcome()`

### `public java.util.UUID siteId()`

### `public final java.lang.String toString()`

### `public boolean unexpected()`
