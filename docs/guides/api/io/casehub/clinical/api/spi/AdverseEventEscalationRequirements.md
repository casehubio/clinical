# io.casehub.clinical.api.spi.AdverseEventEscalationRequirements

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `record`

Policy decision for a reported adverse event.

<p>When `engineCaseRequired` is false, `candidateGroups` is used to
create a WorkItem directly (Layer 2 path). When true, `candidateGroups` is
null and the engine case creates WorkItems via humanTask bindings using
`requiresSeniorMonitor` and `requiresDsmbEscalation` as context keys.

## Fields

### `candidateGroups` (`java.lang.String`)

### `engineCaseRequired` (`boolean`)

### `requiresDsmbEscalation` (`boolean`)

### `requiresSeniorMonitor` (`boolean`)

## Record Components

### `candidateGroups` (`java.lang.String`)

### `engineCaseRequired` (`boolean`)

### `requiresDsmbEscalation` (`boolean`)

### `requiresSeniorMonitor` (`boolean`)

## Constructors

### `public AdverseEventEscalationRequirements(boolean engineCaseRequired, java.lang.String candidateGroups, boolean requiresSeniorMonitor, boolean requiresDsmbEscalation)`

#### Parameters

- `engineCaseRequired` (`boolean`)
- `candidateGroups` (`java.lang.String`)
- `requiresSeniorMonitor` (`boolean`)
- `requiresDsmbEscalation` (`boolean`)

## Methods

### `public java.lang.String candidateGroups()`

### `public static io.casehub.clinical.api.spi.AdverseEventEscalationRequirements direct(java.lang.String candidateGroups)`

#### Parameters

- `candidateGroups` (`java.lang.String`)

### `public boolean engineCaseRequired()`

### `public static io.casehub.clinical.api.spi.AdverseEventEscalationRequirements engineManaged(boolean requiresSeniorMonitor, boolean requiresDsmbEscalation)`

#### Parameters

- `requiresSeniorMonitor` (`boolean`)
- `requiresDsmbEscalation` (`boolean`)

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public boolean requiresDsmbEscalation()`

### `public boolean requiresSeniorMonitor()`

### `public final java.lang.String toString()`
