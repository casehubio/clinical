package io.casehub.clinical.api.view;

public record AgentTrustView(
        String capability, String trustDimension,
        Double trustScore, Double threshold, String maturityPhase,
        int decisionCount, int attestationPositive, int attestationNegative,
        Double endorsementRatio, String distinctTrustDimensions
) {}
