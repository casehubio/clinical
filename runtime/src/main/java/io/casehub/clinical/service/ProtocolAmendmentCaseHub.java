package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.Worker;
import io.casehub.api.model.WorkerResult;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentAdvisor;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Case definition for protocol amendment advisor (Layer 9 — LLM supervisor slot).
 *
 * <p>Augments YAML with a Java-function worker for the protocol-amendment-advisor capability.
 * {@link DefaultProtocolAmendmentAdvisor} always returns PROCEED (stub).
 * Replaced by LlmPlanningStrategy when casehubio/engine#101 lands (clinical#86).
 *
 * <p>Double-checked locking on {@code augmentedDefinition} for thread safety — same pattern
 * as {@link ClinicalSusarOversightCaseHub}.
 */
@ApplicationScoped
public class ProtocolAmendmentCaseHub extends YamlCaseHub {

    @Inject ProtocolAmendmentAdvisor advisor;
    private volatile CaseDefinition augmentedDefinition;

    public ProtocolAmendmentCaseHub() { super("clinical/protocol-amendment.yaml"); }

    @Override
    public CaseDefinition getDefinition() {
        if (augmentedDefinition == null) {
            synchronized (this) {
                if (augmentedDefinition == null) {
                    CaseDefinition def = super.getDefinition();
                    def.getWorkers().add(Worker.builder()
                        .name("protocol-amendment-advisor-worker")
                        .capabilities(List.of(Capability.builder()
                            .name("protocol-amendment-advisor")
                            .inputSchema("{ amendmentId: .amendmentId, trialId: .trialId, proposedChange: .proposedChange }")
                            .outputSchema(".")
                            .build()))
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
                    augmentedDefinition = def;
                }
            }
        }
        return augmentedDefinition;
    }
}
