# io.casehub.clinical.api.spi.AdverseEventEscalationPolicy

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `interface`

Org-customisable policy for adverse event routing and engine case wiring.

<p>The default implementation uses CTCAE v5.0 grades. Organisations override
this SPI to apply site-specific thresholds, team assignments, and scope rules.
This is a vocabulary SPI — a no-op default would break routing; the default
must express meaningful routing behaviour.

## Methods

### `public abstract io.casehub.clinical.api.spi.AdverseEventEscalationRequirements evaluate(io.casehub.clinical.api.spi.AdverseEventContext context)`

#### Parameters

- `context` (`io.casehub.clinical.api.spi.AdverseEventContext`)
