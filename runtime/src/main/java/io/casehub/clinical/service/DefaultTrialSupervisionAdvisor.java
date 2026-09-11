package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.SupervisionAssessment;
import io.casehub.clinical.api.spi.TrialSupervisionAdvisor;
import io.casehub.clinical.api.spi.TrialSupervisionContext;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class DefaultTrialSupervisionAdvisor implements TrialSupervisionAdvisor {

    @Override
    public SupervisionAssessment assess(TrialSupervisionContext context) {
        return new SupervisionAssessment("REVIEW_REQUIRED", List.of(),
                "No LLM analysis available — manual review required");
    }
}
