# io.casehub.clinical.api.IrbApprovalResolvedEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

CDI event fired when an IRB consultation case reaches any terminal decision:
APPROVED, REJECTED, DEFERRED, or EXPIRED.

<p>Follows `ProtocolDeviationResolvedEvent` pattern. Layer 6 consumers
(trial-level aggregation, DSMB rollup) observe this for cross-site signals.

## Fields

### `approvalId` (`java.util.UUID`)

### `decidedAt` (`java.time.Instant`)

### `decision` (`io.casehub.clinical.api.model.IrbDecision`)

### `deviationId` (`java.util.UUID`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

## Record Components

### `approvalId` (`java.util.UUID`)

### `decidedAt` (`java.time.Instant`)

### `decision` (`io.casehub.clinical.api.model.IrbDecision`)

### `deviationId` (`java.util.UUID`)

### `siteId` (`java.util.UUID`)

### `tenantId` (`java.lang.String`)

## Constructors

### `public IrbApprovalResolvedEvent(java.util.UUID approvalId, java.util.UUID deviationId, java.util.UUID siteId, io.casehub.clinical.api.model.IrbDecision decision, java.time.Instant decidedAt, java.lang.String tenantId)`

#### Parameters

- `approvalId` (`java.util.UUID`)
- `deviationId` (`java.util.UUID`)
- `siteId` (`java.util.UUID`)
- `decision` (`io.casehub.clinical.api.model.IrbDecision`)
- `decidedAt` (`java.time.Instant`)
- `tenantId` (`java.lang.String`)

## Methods

### `public java.util.UUID approvalId()`

### `public java.time.Instant decidedAt()`

### `public io.casehub.clinical.api.model.IrbDecision decision()`

### `public java.util.UUID deviationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID siteId()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`
