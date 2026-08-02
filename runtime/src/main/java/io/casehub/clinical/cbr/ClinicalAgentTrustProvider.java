package io.casehub.clinical.cbr;

import io.casehub.clinical.api.ClinicalTrustDimensions;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.OptionalDouble;
import java.util.stream.Stream;

@ApplicationScoped
public class ClinicalAgentTrustProvider implements AgentTrustProvider {

    private static final String[] DIMENSIONS = {
        ClinicalTrustDimensions.SAFETY_ACCURACY,
        ClinicalTrustDimensions.ELIGIBILITY_PRECISION,
        ClinicalTrustDimensions.PROTOCOL_ADHERENCE
    };

    private final TrustScoreSource trustScoreSource;

    @Inject
    public ClinicalAgentTrustProvider(TrustScoreSource trustScoreSource) {
        this.trustScoreSource = trustScoreSource;
    }

    @Override
    public OptionalDouble currentTrustScore(String agentId) {
        double[] scores = Stream.of(DIMENSIONS)
            .map(dim -> trustScoreSource.dimensionScore(agentId, dim))
            .filter(OptionalDouble::isPresent)
            .mapToDouble(OptionalDouble::getAsDouble)
            .toArray();
        if (scores.length == 0) return OptionalDouble.empty();
        double sum = 0;
        for (double s : scores) sum += s;
        return OptionalDouble.of(sum / scores.length);
    }
}
