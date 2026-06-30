package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentAdvisor;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;

/**
 * Case definition for protocol amendment advisor (Layer 9 — LLM supervisor slot).
 *
 * <p>Augments YAML with a Java-function worker for the protocol-amendment-advisor capability.
 * {@link DefaultProtocolAmendmentAdvisor} always returns PROCEED (stub).
 * Replaced by LlmPlanningStrategy when casehubio/engine#101 lands (clinical#86).
 */
@ApplicationScoped
public class ProtocolAmendmentCaseHub extends YamlCaseHub {

    @Inject ProtocolAmendmentAdvisor advisor;

    public ProtocolAmendmentCaseHub() { super("clinical/protocol-amendment.yaml"); }

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().add(Worker.builder()
            .name("protocol-amendment-advisor-worker")
            .capabilityName("protocol-amendment-advisor")
            .function((Map<String, Object> ctx) -> {
                String amendmentIdStr = (String) ctx.get("amendmentId");
                String trialIdStr = (String) ctx.get("trialId");
                if (amendmentIdStr == null || trialIdStr == null) {
                    return WorkerResult.failed("protocol-amendment-advisor: missing context keys (amendmentId, trialId)");
                }
                ProtocolAmendmentContext pac = new ProtocolAmendmentContext(
                    UUID.fromString(amendmentIdStr),
                    UUID.fromString(trialIdStr),
                    (String) ctx.get("proposedChange"),
                    Map.of()
                );
                AmendmentRecommendation rec = advisor.advise(pac);
                return WorkerResult.of(Map.of("advisorRecommendation", rec.name()));
            })
            .build());
    }
}
