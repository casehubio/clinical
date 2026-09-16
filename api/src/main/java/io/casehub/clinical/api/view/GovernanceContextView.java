package io.casehub.clinical.api.view;

public record GovernanceContextView(
        String grade, boolean unexpected, boolean suspected,
        String susarOversightStatus,
        String workerId, String capabilityTag,
        Double trustScoreAtRouting, Double thresholdApplied,
        Double currentTrustScore,
        String gateStatus
) {}
