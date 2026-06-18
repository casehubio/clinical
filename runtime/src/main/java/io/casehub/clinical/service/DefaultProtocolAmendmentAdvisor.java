package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentAdvisor;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Stub implementation — always recommends PROCEED.
 *
 * <p>Replace with LlmPlanningStrategy integration when casehubio/engine#101 lands.
 * Tracked: casehubio/clinical#86.
 *
 * <p>CDI displacement: an {@code @ApplicationScoped} bean without {@code @DefaultBean}
 * implementing {@link ProtocolAmendmentAdvisor} automatically displaces this stub.
 */
@DefaultBean
@ApplicationScoped
public class DefaultProtocolAmendmentAdvisor implements ProtocolAmendmentAdvisor {

    @Override
    public AmendmentRecommendation advise(ProtocolAmendmentContext context) {
        return AmendmentRecommendation.PROCEED;
    }
}
