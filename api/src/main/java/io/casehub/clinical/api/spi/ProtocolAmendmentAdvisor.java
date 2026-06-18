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
 * <p>LLM supervisor integration pending casehubio/engine#101 (LlmPlanningStrategy SPI).
 * Tracked: casehubio/clinical#86.
 */
public interface ProtocolAmendmentAdvisor {
    AmendmentRecommendation advise(ProtocolAmendmentContext context);
}
