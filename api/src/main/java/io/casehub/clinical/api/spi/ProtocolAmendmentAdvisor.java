package io.casehub.clinical.api.spi;

/**
 * SPI for protocol amendment advisory decisions.
 *
 * <p>Called by {@code io.casehub.clinical.service.ProtocolAmendmentCaseHub} when an amendment
 * case starts. Implementations must return an {@link AmendmentRecommendation} synchronously.
 *
 * <p>Default implementation: {@code DefaultProtocolAmendmentAdvisor} always returns
 * {@link AmendmentRecommendation#PROCEED} (stub). Override by registering an
 * {@code @ApplicationScoped} bean without {@code @DefaultBean} — CDI priority resolution
 * displaces the default automatically.
 *
 * <p>LLM-backed implementation: {@code LlmProtocolAmendmentAdvisor} uses {@code AgentProvider}
 * to delegate to an LLM for context-aware recommendations (casehubio/clinical#86).
 */
public interface ProtocolAmendmentAdvisor {
    AmendmentRecommendation advise(ProtocolAmendmentContext context);
}
