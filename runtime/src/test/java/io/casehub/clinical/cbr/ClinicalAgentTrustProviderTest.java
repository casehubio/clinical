package io.casehub.clinical.cbr;

import io.casehub.ledger.api.spi.TrustScoreSource;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClinicalAgentTrustProviderTest {

    @Test
    void returnsAverageOfAllDimensionScores() {
        TrustScoreSource source = mock(TrustScoreSource.class);
        when(source.dimensionScore("agent-1", "safety-accuracy")).thenReturn(OptionalDouble.of(0.9));
        when(source.dimensionScore("agent-1", "eligibility-precision")).thenReturn(OptionalDouble.of(0.8));
        when(source.dimensionScore("agent-1", "protocol-adherence")).thenReturn(OptionalDouble.of(0.7));

        ClinicalAgentTrustProvider provider = new ClinicalAgentTrustProvider(source);
        OptionalDouble score = provider.currentTrustScore("agent-1");

        assertTrue(score.isPresent());
        assertEquals(0.8, score.getAsDouble(), 0.001);
    }

    @Test
    void returnsEmptyWhenNoDimensionScores() {
        TrustScoreSource source = mock(TrustScoreSource.class);
        when(source.dimensionScore(anyString(), anyString())).thenReturn(OptionalDouble.empty());

        ClinicalAgentTrustProvider provider = new ClinicalAgentTrustProvider(source);
        assertTrue(provider.currentTrustScore("unknown-agent").isEmpty());
    }

    @Test
    void averagesOnlyPresentDimensions() {
        TrustScoreSource source = mock(TrustScoreSource.class);
        when(source.dimensionScore("agent-1", "safety-accuracy")).thenReturn(OptionalDouble.of(0.6));
        when(source.dimensionScore("agent-1", "eligibility-precision")).thenReturn(OptionalDouble.empty());
        when(source.dimensionScore("agent-1", "protocol-adherence")).thenReturn(OptionalDouble.of(0.8));

        ClinicalAgentTrustProvider provider = new ClinicalAgentTrustProvider(source);
        OptionalDouble score = provider.currentTrustScore("agent-1");

        assertTrue(score.isPresent());
        assertEquals(0.7, score.getAsDouble(), 0.001);
    }

    @Test
    void singleDimensionPresent() {
        TrustScoreSource source = mock(TrustScoreSource.class);
        when(source.dimensionScore("agent-1", "safety-accuracy")).thenReturn(OptionalDouble.of(0.95));
        when(source.dimensionScore("agent-1", "eligibility-precision")).thenReturn(OptionalDouble.empty());
        when(source.dimensionScore("agent-1", "protocol-adherence")).thenReturn(OptionalDouble.empty());

        ClinicalAgentTrustProvider provider = new ClinicalAgentTrustProvider(source);
        OptionalDouble score = provider.currentTrustScore("agent-1");

        assertTrue(score.isPresent());
        assertEquals(0.95, score.getAsDouble(), 0.001);
    }
}
