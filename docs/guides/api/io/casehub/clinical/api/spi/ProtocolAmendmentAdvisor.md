# io.casehub.clinical.api.spi.ProtocolAmendmentAdvisor

**Package:** `io.casehub.clinical.api.spi`

**Kind:** `interface`

SPI for protocol amendment advisory decisions.

<p>Called by `io.casehub.clinical.service.ProtocolAmendmentCaseHub` when an amendment
case starts. Implementations must return an `AmendmentRecommendation` synchronously.

<p>Default implementation: `DefaultProtocolAmendmentAdvisor` always returns
`AmendmentRecommendation.PROCEED` (stub). Override by registering an
`@ApplicationScoped` bean without `@DefaultBean` — CDI priority resolution
displaces the default automatically.

<p>LLM-backed implementation: `LlmProtocolAmendmentAdvisor` uses `AgentProvider`
to delegate to an LLM for context-aware recommendations (casehubio/clinical#86).

## Methods

### `public abstract io.casehub.clinical.api.spi.AmendmentRecommendation advise(io.casehub.clinical.api.spi.ProtocolAmendmentContext context)`

#### Parameters

- `context` (`io.casehub.clinical.api.spi.ProtocolAmendmentContext`)
