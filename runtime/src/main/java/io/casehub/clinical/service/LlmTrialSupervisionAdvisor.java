package io.casehub.clinical.service;

import io.casehub.clinical.agent.ClinicalAgentRequest;
import io.casehub.clinical.agent.ClinicalAgentResult;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.clinical.api.spi.SupervisionAssessment;
import io.casehub.clinical.api.spi.TrialSupervisionAdvisor;
import io.casehub.clinical.api.spi.TrialSupervisionContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LlmTrialSupervisionAdvisor implements TrialSupervisionAdvisor {

    private static final Logger LOG = Logger.getLogger(LlmTrialSupervisionAdvisor.class);

    static final String SYSTEM_PROMPT = """
            You are a clinical trial operations advisor. Assess trial-wide operational health \
            across all sites.

            Identify:
            - Enrollment trajectory anomalies (sites falling behind target)
            - Protocol adherence degradation (rising deviation rates)
            - Site performance outliers (disproportionate AE rates or slow responses)
            - Cross-site patterns that warrant sponsor attention

            Recommend concrete operational interventions. You are advisory — operations teams \
            and PIs make binding decisions.

            Respond with JSON only:
            {"overallHealth":"HEALTHY|WATCH|CONCERN|ACTION_REQUIRED",\
            "findings":[{"findingType":"enrollment_lag","affectedSites":["site-1"],\
            "severity":"MODERATE","narrative":"...","recommendedAction":"..."}],\
            "summary":"one paragraph"}
            """;

    private final ClinicalAgentSupport agentSupport;

    @Inject
    public LlmTrialSupervisionAdvisor(ClinicalAgentSupport agentSupport) {
        this.agentSupport = agentSupport;
    }

    @Override
    public SupervisionAssessment assess(TrialSupervisionContext context) {
        SupervisionAssessment fallback = new SupervisionAssessment(
                "REVIEW_REQUIRED", List.of(),
                "LLM analysis unavailable — manual review required");

        String userPrompt = buildUserPrompt(context);

        var request = new ClinicalAgentRequest<>(
                SYSTEM_PROMPT, userPrompt, SupervisionAssessment.class,
                fallback, "supervision",
                context.trialId() != null ? context.trialId().toString() : null);

        ClinicalAgentResult<SupervisionAssessment> result = agentSupport.invoke(request);

        if (result.fallbackUsed()) {
            LOG.warnf("LlmTrialSupervisionAdvisor: fallback used — %s", result.failureReason());
            return fallback;
        }

        return result.response();
    }

    private String buildUserPrompt(TrialSupervisionContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Trial Context\n");
        sb.append("- Trial ID: ").append(context.trialId()).append("\n");
        sb.append("- Phase: ").append(context.trialPhase()).append("\n\n");

        Map<String, Object> ctx = context.caseContext();
        if (ctx != null && !ctx.isEmpty()) {
            sb.append("## Case Context Data\n");
            for (var entry : ctx.entrySet()) {
                sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        return sb.toString();
    }
}
