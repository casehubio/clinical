# io.casehub.clinical.api.spi.IrbCommitteeAssignmentPolicy

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `interface`

Maps deviation context to an IRB committee assignment.
Mirrors `DeviationResponsePolicy` — implement as
`@ApplicationScoped @Alternative @Priority(1)` to override the default.

## Methods

### `public abstract io.casehub.clinical.api.spi.IrbCommitteeAssignment evaluate(io.casehub.clinical.api.spi.IrbCommitteeContext context)`

#### Parameters

- `context` (`io.casehub.clinical.api.spi.IrbCommitteeContext`)
