package io.casehub.clinical.api.spi;

public interface TrialSupervisionAdvisor {
    SupervisionAssessment assess(TrialSupervisionContext context);
}
