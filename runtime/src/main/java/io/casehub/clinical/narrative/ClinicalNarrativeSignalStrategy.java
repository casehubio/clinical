package io.casehub.clinical.narrative;

import io.casehub.api.spi.StepOutcomeEvent;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.narrative.AbstractNarrativeSignalStrategy;
import io.casehub.blocks.summarisation.narrative.CbrRetrieval;
import io.casehub.blocks.summarisation.narrative.DecisionNarrativePipeline;
import io.casehub.blocks.summarisation.narrative.DecisionSignal;
import io.casehub.blocks.summarisation.narrative.RoutingDecision;
import io.casehub.blocks.summarisation.narrative.StepOutcome;
import io.casehub.blocks.summarisation.narrative.TrustAssessment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
@Alternative
@Priority(1)
public class ClinicalNarrativeSignalStrategy extends AbstractNarrativeSignalStrategy {

    private static final Set<String> CASE_TYPES = Set.of(
            "trial-coordination", "susar-oversight", "ae-escalation",
            "eligibility-screening", "protocol-amendment",
            "deviation-review", "regulatory-submission"
    );

    @Inject
    public ClinicalNarrativeSignalStrategy(EventStreamBus<DecisionSignal> signalBus,
                                            DecisionNarrativePipeline pipeline) {
        super(signalBus, pipeline);
    }

    @Override
    public void onStepOutcome(Object event) {
        if (!(event instanceof StepOutcomeEvent step)) return;
        if (!CASE_TYPES.contains(step.caseType())) return;

        String caseId = step.caseId().toString();
        String stepName = step.bindingName();
        Instant now = Instant.now();

        emit(new StepOutcome(caseId, stepName, now,
                step.outcome().name(), step.workerName(),
                null, step.executionDuration() != null ? step.executionDuration() : Duration.ZERO));

        Map<String, Object> ctx = step.contextSnapshot();
        if (ctx == null) return;

        String routedAgent = (String) ctx.get("routedAgentId");
        if (routedAgent != null) {
            double score = parseScore(ctx.get("routingScore"));
            emit(new RoutingDecision(caseId, stepName, now,
                    routedAgent, "trust-weighted", score,
                    List.of(step.workerName()), null));
        }

        double trustScore = parseScore(ctx.get("trustScore"));
        if (trustScore > 0.0) {
            emit(new TrustAssessment(caseId, stepName, now,
                    step.workerName(), trustScore, 0.0, true));
        }

        int cbrCount = parseInt(ctx.get("cbrRetrievedCount"));
        if (cbrCount > 0) {
            double similarity = parseScore(ctx.get("cbrTopSimilarity"));
            emit(new CbrRetrieval(caseId, stepName, now,
                    cbrCount, similarity, null, "clinical"));
        }
    }

    @Override
    protected @Nullable String extractTenancyId(DecisionSignal signal) {
        return null;
    }

    private static double parseScore(Object value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int parseInt(Object value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
