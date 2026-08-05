# io.casehub.clinical.api.SponsorNotificationExhaustedEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

Fired when all retry attempts for a sponsor notification are consumed and
the sponsor remains unreached. Named "Exhausted" (not "Failed") because
FAILED is an intermediate status meaning retries remain.

<p>No consumer is defined in this version — this is the extension point for
WorkItem escalation (casehubio/clinical#60).

## Fields

### `deviationId` (`java.util.UUID`)

### `failureReason` (`java.lang.String`)

### `notificationId` (`java.util.UUID`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `terminalStatus` (`io.casehub.clinical.api.model.PiApprovalStatus`)

### `totalAttempts` (`int`)

### `trialId` (`java.util.UUID`)

## Record Components

### `deviationId` (`java.util.UUID`)

### `failureReason` (`java.lang.String`)

### `notificationId` (`java.util.UUID`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `terminalStatus` (`io.casehub.clinical.api.model.PiApprovalStatus`)

### `totalAttempts` (`int`)

### `trialId` (`java.util.UUID`)

## Constructors

### `public SponsorNotificationExhaustedEvent(java.util.UUID notificationId, java.util.UUID deviationId, java.util.UUID trialId, java.util.UUID siteId, io.casehub.clinical.api.model.DeviationSeverity severity, io.casehub.clinical.api.model.PiApprovalStatus terminalStatus, java.lang.String failureReason, int totalAttempts)`

#### Parameters

- `notificationId` (`java.util.UUID`)
- `deviationId` (`java.util.UUID`)
- `trialId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)
- `terminalStatus` (`io.casehub.clinical.api.model.PiApprovalStatus`)
- `failureReason` (`java.lang.String`)
- `totalAttempts` (`int`)

## Methods

### `public java.util.UUID deviationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String failureReason()`

### `public final int hashCode()`

### `public java.util.UUID notificationId()`

### `public io.casehub.clinical.api.model.DeviationSeverity severity()`

### `public java.util.UUID siteId()`

### `public io.casehub.clinical.api.model.PiApprovalStatus terminalStatus()`

### `public final java.lang.String toString()`

### `public int totalAttempts()`

### `public java.util.UUID trialId()`
