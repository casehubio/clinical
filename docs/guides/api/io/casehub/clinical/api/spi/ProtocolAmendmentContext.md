# io.casehub.clinical.api.spi.ProtocolAmendmentContext

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `record`

## Fields

### `amendmentId` (`java.util.UUID`)

### `proposedChange` (`java.lang.String`)

### `trialBlackboardSnapshot` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `trialId` (`java.util.UUID`)

## Record Components

### `amendmentId` (`java.util.UUID`)

### `proposedChange` (`java.lang.String`)

### `trialBlackboardSnapshot` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `trialId` (`java.util.UUID`)

## Constructors

### `public ProtocolAmendmentContext(java.util.UUID amendmentId, java.util.UUID trialId, java.lang.String proposedChange, java.util.Map<java.lang.String,java.lang.Object> trialBlackboardSnapshot)`

#### Parameters

- `amendmentId` (`java.util.UUID`)
- `trialId` (`java.util.UUID`)
- `proposedChange` (`java.lang.String`)
- `trialBlackboardSnapshot` (`java.util.Map<java.lang.String,java.lang.Object>`)

## Methods

### `public java.util.UUID amendmentId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String proposedChange()`

### `public final java.lang.String toString()`

### `public java.util.Map<java.lang.String,java.lang.Object> trialBlackboardSnapshot()`

### `public java.util.UUID trialId()`
