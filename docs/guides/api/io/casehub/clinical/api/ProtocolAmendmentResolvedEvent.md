# io.casehub.clinical.api.ProtocolAmendmentResolvedEvent

**Package:** `io.casehub.clinical.api`

**Kind:** `record`

Fired when a protocol amendment reaches terminal resolution: APPROVED, HALTED, or SUPERVISED.
<p>
Consumers (e.g., CBR writers) can react to record precedent for future amendment decisions.

## Fields

### `amendmentId` (`java.util.UUID`)

### `recommendation` (`io.casehub.clinical.api.spi.AmendmentRecommendation`)

### `tenantId` (`java.lang.String`)

### `terminalStatus` (`io.casehub.clinical.api.model.ProtocolAmendmentStatus`)

### `trialId` (`java.util.UUID`)

## Record Components

### `amendmentId` (`java.util.UUID`)

amendment entity identifier

### `recommendation` (`io.casehub.clinical.api.spi.AmendmentRecommendation`)

supervisor recommendation: PROCEED, HALT, or REFER_TO_DSMB

### `tenantId` (`java.lang.String`)

tenant identifier

### `terminalStatus` (`io.casehub.clinical.api.model.ProtocolAmendmentStatus`)

final status: APPROVED, HALTED, or SUPERVISED

### `trialId` (`java.util.UUID`)

trial this amendment belongs to

## Constructors

### `public ProtocolAmendmentResolvedEvent(java.util.UUID amendmentId, java.util.UUID trialId, io.casehub.clinical.api.model.ProtocolAmendmentStatus terminalStatus, io.casehub.clinical.api.spi.AmendmentRecommendation recommendation, java.lang.String tenantId)`

#### Parameters

- `amendmentId` (`java.util.UUID`)
- `trialId` (`java.util.UUID`)
- `terminalStatus` (`io.casehub.clinical.api.model.ProtocolAmendmentStatus`)
- `recommendation` (`io.casehub.clinical.api.spi.AmendmentRecommendation`)
- `tenantId` (`java.lang.String`)

## Methods

### `public java.util.UUID amendmentId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.clinical.api.spi.AmendmentRecommendation recommendation()`

### `public java.lang.String tenantId()`

### `public io.casehub.clinical.api.model.ProtocolAmendmentStatus terminalStatus()`

### `public final java.lang.String toString()`

### `public java.util.UUID trialId()`
