# io.casehub.clinical.api.spi.IrbCommitteeAssignment

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `record`

Assignment returned by `IrbCommitteeAssignmentPolicy.evaluate`.

## Fields

### `candidateGroups` (`java.util.List<java.lang.String>`)

### `committeeId` (`java.lang.String`)

## Record Components

### `candidateGroups` (`java.util.List<java.lang.String>`)

### `committeeId` (`java.lang.String`)

## Constructors

### `public IrbCommitteeAssignment(java.lang.String committeeId, java.util.List<java.lang.String> candidateGroups)`

#### Parameters

- `committeeId` (`java.lang.String`)
- `candidateGroups` (`java.util.List<java.lang.String>`)

## Methods

### `public java.util.List<java.lang.String> candidateGroups()`

### `public java.lang.String committeeId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`
