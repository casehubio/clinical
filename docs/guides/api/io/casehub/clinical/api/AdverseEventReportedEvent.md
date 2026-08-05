# io.casehub.clinical.api.AdverseEventReportedEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

CDI event fired when a Grade 3+ adverse event is reported and requires
engine-managed escalation. Grade 1/2 AEs use direct WorkItem creation
and do not fire this event.

<p>Consumer: `AeEscalationCaseService` — starts the AE escalation
engine case and creates humanTask WorkItems via the YAML bindings.

## Fields

### `aeId` (`java.util.UUID`)

### `enrollmentId` (`java.util.UUID`)

### `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `reportedAt` (`java.time.Instant`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

## Record Components

### `aeId` (`java.util.UUID`)

### `enrollmentId` (`java.util.UUID`)

### `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `reportedAt` (`java.time.Instant`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

## Constructors

### `public AdverseEventReportedEvent(java.util.UUID aeId, java.util.UUID enrollmentId, java.util.UUID siteId, io.casehub.clinical.api.model.CtcaeGrade grade, java.time.Instant reportedAt, java.lang.String tenantId)`

#### Parameters

- `aeId` (`java.util.UUID`)
- `enrollmentId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)
- `reportedAt` (`java.time.Instant`)
- `tenantId` (`java.lang.String`)

## Methods

### `public java.util.UUID aeId()`

### `public java.util.UUID enrollmentId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.clinical.api.model.CtcaeGrade grade()`

### `public final int hashCode()`

### `public java.time.Instant reportedAt()`

### `public java.util.UUID siteId()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`
