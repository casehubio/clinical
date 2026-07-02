package io.casehub.clinical.routing;

import io.casehub.blocks.oversight.ActionRiskClassifier;
import io.casehub.blocks.oversight.ClassificationContext;
import io.casehub.worker.api.PlannedAction;
import io.casehub.blocks.oversight.RiskClassifier;
import io.casehub.blocks.oversight.RiskDecision;
import io.casehub.clinical.api.model.ClinicalActionType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * Clinical-domain {@link ActionRiskClassifier}. Discovered by casehub-engine via
 * {@link RiskClassifier} CDI qualifier and composed automatically with other classifiers
 * via {@code ChainedReactiveActionRiskClassifier.mostRestrictive()}.
 *
 * <p>All five clinical action types are unconditionally gated (ALWAYS policy) — these
 * are regulatory obligations (GCP, ICH E2A, 21 CFR Part 312), not configurable thresholds.
 *
 * <p>Unknown action types return {@link RiskDecision.Autonomous} — this classifier does
 * not gate actions it does not own.
 */
@ApplicationScoped
@RiskClassifier
public class ClinicalActionRiskClassifier implements ActionRiskClassifier {

    @Override
    public RiskDecision classify(final PlannedAction action, final ClassificationContext context) {
        Optional<ClinicalActionType> typeOpt = ClinicalActionType.fromActionType(
                action != null ? action.actionType() : null);
        if (typeOpt.isEmpty()) {
            return new RiskDecision.Autonomous();
        }
        final ClinicalActionType type = typeOpt.get();
        return new RiskDecision.GateRequired(
                type.reason(), type.reversible(), type.candidateGroups(),
                type.expiresIn(), type.scope());
    }
}
