# io.casehub.clinical.api.ProtocolDeviationResolvedEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

Fired when a protocol deviation reaches a terminal PI authorisation state.
Consumers: casehubio/clinical#6 (IRB_REVIEW) and casehubio/clinical#13 (SPONSOR_NOTIFICATION).

<p>`piId` is null when `terminalStatus` is EXPIRED (system-initiated).
Consumers that require a named PI must check before use.

## Fields

### `deviationId` (`java.util.UUID`)

### `deviationType` (`java.lang.String`)

### `escalationRequirement` (`io.casehub.clinical.api.model.EscalationRequirement`)

### `piId` (`java.lang.String`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

### `terminalStatus` (`io.casehub.clinical.api.model.PiApprovalStatus`)

## Record Components

### `deviationId` (`java.util.UUID`)

### `deviationType` (`java.lang.String`)

### `escalationRequirement` (`io.casehub.clinical.api.model.EscalationRequirement`)

### `piId` (`java.lang.String`)

### `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

### `terminalStatus` (`io.casehub.clinical.api.model.PiApprovalStatus`)

## Constructors

### `public ProtocolDeviationResolvedEvent(java.util.UUID deviationId, java.util.UUID siteId, io.casehub.clinical.api.model.DeviationSeverity severity, io.casehub.clinical.api.model.EscalationRequirement escalationRequirement, io.casehub.clinical.api.model.PiApprovalStatus terminalStatus, java.lang.String deviationType, java.lang.String piId, java.lang.String tenantId)`

#### Parameters

- `deviationId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `severity` (`io.casehub.clinical.api.model.DeviationSeverity`)
- `escalationRequirement` (`io.casehub.clinical.api.model.EscalationRequirement`)
- `terminalStatus` (`io.casehub.clinical.api.model.PiApprovalStatus`)
- `deviationType` (`java.lang.String`)
- `piId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

## Methods

### `public java.util.UUID deviationId()`

### `public java.lang.String deviationType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.clinical.api.model.EscalationRequirement escalationRequirement()`

### `public final int hashCode()`

### `public java.lang.String piId()`

### `public io.casehub.clinical.api.model.DeviationSeverity severity()`

### `public java.util.UUID siteId()`

### `public java.lang.String tenantId()`

### `public io.casehub.clinical.api.model.PiApprovalStatus terminalStatus()`

### `public final java.lang.String toString()`
