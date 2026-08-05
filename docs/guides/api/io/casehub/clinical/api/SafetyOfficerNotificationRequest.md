# io.casehub.clinical.api.SafetyOfficerNotificationRequest

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

Carries all context needed for a safety officer adverse event notification.

## Fields

### `aeId` (`java.util.UUID`)

### `connectorId` (`java.lang.String`)

### `destination` (`java.lang.String`)

### `enrollmentId` (`java.util.UUID`)

### `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)

### `siteId` (`java.util.UUID`)

## Record Components

### `aeId` (`java.util.UUID`)

the adverse event being reported

### `connectorId` (`java.lang.String`)

the connector to use for delivery (e.g. "slack", "teams")

### `destination` (`java.lang.String`)

connector-specific target (e.g. webhook URL, email address)

### `enrollmentId` (`java.util.UUID`)

the patient enrollment associated with the event

### `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)

CTCAE v5.0 grade — Grade 5 (Death) warrants CRITICAL urgency

### `siteId` (`java.util.UUID`)

the trial site where the event occurred

## Constructors

### `public SafetyOfficerNotificationRequest(java.util.UUID aeId, java.util.UUID enrollmentId, java.util.UUID siteId, io.casehub.clinical.api.model.CtcaeGrade grade, java.lang.String connectorId, java.lang.String destination)`

#### Parameters

- `aeId` (`java.util.UUID`)
- `enrollmentId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `grade` (`io.casehub.clinical.api.model.CtcaeGrade`)
- `connectorId` (`java.lang.String`)
- `destination` (`java.lang.String`)

## Methods

### `public java.util.UUID aeId()`

### `public java.lang.String connectorId()`

### `public java.lang.String destination()`

### `public java.util.UUID enrollmentId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.clinical.api.model.CtcaeGrade grade()`

### `public final int hashCode()`

### `public java.util.UUID siteId()`

### `public final java.lang.String toString()`
