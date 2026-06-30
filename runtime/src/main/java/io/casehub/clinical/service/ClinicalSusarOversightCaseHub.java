package io.casehub.clinical.service;

import io.casehub.api.engine.YamlCaseHub;
import io.casehub.api.model.CaseDefinition;
import io.casehub.worker.api.Worker;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Case definition for SUSAR expedited safety report oversight (Layer 8).
 *
 * <p>Augments the YAML definition with the {@link SusarCriteriaEvaluator} worker,
 * registered against the {@code safety-monitoring} capability. The worker fires once
 * per case on valid {@code aeId} in context, evaluates SUSAR criteria from the
 * persisted entity, and returns a {@link io.casehub.worker.api.PlannedAction} requiring
 * qualified-investigator sign-off.
 */
@ApplicationScoped
public class ClinicalSusarOversightCaseHub extends YamlCaseHub {

    @Inject SusarEvaluatorFunction susarEvaluator;

    public ClinicalSusarOversightCaseHub() { super("clinical/susar-oversight.yaml"); }

    @Override
    protected void augment(CaseDefinition definition) {
        definition.getWorkers().add(Worker.builder()
                .name("susar-criteria-evaluator")
                .capabilityName("safety-monitoring")
                .function(susarEvaluator)
                .build());
    }
}
