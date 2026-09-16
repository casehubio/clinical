package io.casehub.clinical.service.api;

import io.casehub.clinical.api.spi.ClinicalEscalationPlanApi;
import io.casehub.clinical.api.view.EscalationPlanView;
import io.casehub.clinical.api.view.EscalationStepView;
import io.casehub.clinical.cbr.AeEscalationPlanRetriever;
import io.casehub.clinical.cbr.EscalationPlanRecommendation;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalEscalationPlanApi implements ClinicalEscalationPlanApi {

    @Inject AeEscalationPlanRetriever planRetriever;
    @Inject
            jakarta.persistence.EntityManager em;


    @Override
    @Transactional
    public EscalationPlanView getEscalationPlans(UUID aeId, String tenancyId) {
        AdverseEvent ae = em.createNamedQuery("AdverseEvent.findByIdAndTenantId", AdverseEvent.class).setParameter("id", aeId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (ae == null) throw new NotFoundException("Adverse event not found: " + aeId);
        EscalationPlanRecommendation recommendation = planRetriever.retrieve(ae);
        return toView(recommendation);
    }

    private static EscalationPlanView toView(EscalationPlanRecommendation rec) {
        List<EscalationStepView> steps = rec.adaptedPlan() != null
                ? rec.adaptedPlan().steps().stream().map(DefaultClinicalEscalationPlanApi::toStepView).toList()
                : List.of();
        return new EscalationPlanView(rec.retrievedCaseCount(), rec.topSimilarityScore(),
                rec.traceId(), rec.explanation(), steps);
    }

    private static EscalationStepView toStepView(AdaptedStep step) {
        return new EscalationStepView(step.bindingName(), step.capabilityName(),
                step.workerName(), step.stepOutcome(), step.action().name(),
                step.priority(), step.reason());
    }
}
