package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.worker.api.Capability;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Case definition for SUSAR expedited safety report oversight (Layer 8).
 *
 * <p>Augments the YAML definition with the {@link SusarCriteriaEvaluator} worker,
 * registered against the {@code safety-monitoring} capability. The worker fires once
 * per case on valid {@code aeId} in context, evaluates SUSAR criteria from the
 * persisted entity, and returns a {@link io.casehub.api.spi.PlannedAction} requiring
 * qualified-investigator sign-off.
 *
 * <p>CDI displacement: annotate a replacement evaluator with {@code @ApplicationScoped}
 * (no {@code @DefaultBean}) to displace the rule-based default automatically.
 */
@ApplicationScoped
public class ClinicalSusarOversightCaseHub extends YamlCaseHub {

    @Inject SusarEvaluatorFunction susarEvaluator;
    private volatile CaseDefinition augmentedDefinition;

    public ClinicalSusarOversightCaseHub() { super("clinical/susar-oversight.yaml"); }

    @Override
    public CaseDefinition getDefinition() {
        if (augmentedDefinition == null) {
            synchronized (this) {
                if (augmentedDefinition == null) {
                    CaseDefinition def = super.getDefinition();
                    def.getWorkers().add(Worker.builder()
                            .name("susar-criteria-evaluator")
                            .capabilities(List.of(Capability.builder()
                                    .name("safety-monitoring")
                                    .inputSchema("{ aeId: .aeId }")
                                    .outputSchema(".")
                                    .build()))
                            .function(susarEvaluator)
                            .build());
                    augmentedDefinition = def;
                }
            }
        }
        return augmentedDefinition;
    }
}
