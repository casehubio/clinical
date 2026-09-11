package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.clinical.api.spi.SupervisionAssessment;
import io.casehub.clinical.api.spi.TrialSupervisionAdvisor;
import io.casehub.clinical.api.spi.TrialSupervisionContext;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.UUID;

/** Case definition for trial-level cross-site safety coordination (Layer 6). */
@ApplicationScoped
public class ClinicalTrialCaseHub extends YamlCaseHub {

    @Inject TrialSupervisionAdvisor supervisionAdvisor;

    public ClinicalTrialCaseHub() {
        super("clinical/trial-coordination.yaml");
    }

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().add(Worker.<Map<String, Object>, Map<String, Object>>builder()
                .name("trial-supervision-advisor")
                .capabilityName("trial-supervision")
                .function(context -> {
                    String trialId = (String) context.get("trialId");
                    String trialPhase = (String) context.get("trialPhase");
                    var ctx = new TrialSupervisionContext(
                            trialId != null ? UUID.fromString(trialId) : null,
                            trialPhase, context);
                    SupervisionAssessment assessment = supervisionAdvisor.assess(ctx);
                    return WorkerResult.of(Map.of(
                            "overallHealth", assessment.overallHealth(),
                            "findingsCount", assessment.findings().size(),
                            "summary", assessment.summary()));
                })
                .build());
    }
}
