package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Case definition for Grade 3+ adverse event safety escalation (Layer 5).
 *
 * <p>Augments the YAML definition with the {@link SusarCriteriaEvaluator} worker
 * (Layer 8), registered against the {@code safety-monitoring} capability.
 * The {@link SusarEvaluatorFunction} injection point enables CDI displacement:
 * a future ML agent implements the interface as {@code @ApplicationScoped} (no
 * {@code @DefaultBean}) and is selected automatically by Quarkus ArC.
 */
@ApplicationScoped
public class ClinicalAdverseEventCaseHub extends YamlCaseHub {

    @Inject SusarEvaluatorFunction susarEvaluator;

    private volatile CaseDefinition augmentedDefinition;

    public ClinicalAdverseEventCaseHub() {
        super("clinical/ae-escalation.yaml");
    }

    @Override
    public CaseDefinition getDefinition() {
        if (augmentedDefinition == null) {
            synchronized (this) {
                if (augmentedDefinition == null) {
                    final CaseDefinition def = super.getDefinition();
                    def.getWorkers().add(Worker.builder()
                            .name("susar-criteria-evaluator")
                            .capabilities(List.of(Capability.builder()
                                    .name("safety-monitoring")
                                    .inputSchema(".")
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
