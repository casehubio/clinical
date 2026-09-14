package io.casehub.clinical.service;

import io.casehub.clinical.agent.ClinicalAgentRequest;
import io.casehub.clinical.agent.ClinicalAgentResult;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentAdvisor;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;

@ApplicationScoped
public class LlmProtocolAmendmentAdvisor implements ProtocolAmendmentAdvisor {

    private static final Logger LOG = Logger.getLogger(LlmProtocolAmendmentAdvisor.class);

    private static final String SYSTEM_PROMPT = """
            You are a clinical trial protocol amendment advisor with expertise in GCP (ICH E6(R3)), \
            FDA IND requirements, and DSMB governance.
            
            Given a proposed protocol amendment and the trial's current safety profile, recommend one of:
            - PROCEED — the amendment is safe to implement (administrative, low-risk, or expected AE profile)
            - REFER_TO_DSMB — the amendment warrants Data Safety Monitoring Board review \
              (elevated Grade 3+ AE rate, safety-impacting change, cross-site safety signals)
            - HALT — the amendment should not proceed \
              (Grade 5 AEs present, trial integrity at risk, proposed change impacts primary safety endpoints)
            
            Respond with JSON only: {"recommendation": "<PROCEED|REFER_TO_DSMB|HALT>", "reasoning": "<one paragraph>"}
            """;

    private final ClinicalAgentSupport agentSupport;

    @Inject
    public LlmProtocolAmendmentAdvisor(ClinicalAgentSupport agentSupport) {
        this.agentSupport = agentSupport;
    }

    record AmendmentResponse(String recommendation, String reasoning) {}

    @Override
    public AmendmentRecommendation advise(ProtocolAmendmentContext context) {
        String userPrompt = buildUserPrompt(context);
        var request = new ClinicalAgentRequest<>(
                SYSTEM_PROMPT, userPrompt, AmendmentResponse.class,
                new AmendmentResponse("PROCEED", "LLM unavailable — defaulting to PROCEED"),
                "amendment", context.trialId() != null ? context.trialId().toString() : null, null);

        ClinicalAgentResult<AmendmentResponse> result = agentSupport.invoke(request);

        if (result.fallbackUsed()) {
            LOG.warnf("LlmProtocolAmendmentAdvisor: fallback used — %s", result.failureReason());
        }

        try {
            return AmendmentRecommendation.valueOf(result.response().recommendation());
        } catch (IllegalArgumentException e) {
            LOG.warnf("LlmProtocolAmendmentAdvisor: unknown recommendation '%s' — defaulting to PROCEED",
                      result.response().recommendation());
            return AmendmentRecommendation.PROCEED;
        }
    }

    private String buildUserPrompt(ProtocolAmendmentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Proposed Amendment\n");
        sb.append(context.proposedChange()).append("\n\n");
        Map<String, Object> snapshot = context.trialBlackboardSnapshot();
        if (snapshot != null && !snapshot.isEmpty()) {
            sb.append("## Trial Context\n");
            Object phase = snapshot.get("trialPhase");
            if (phase != null) sb.append("- Trial phase: ").append(phase).append("\n");
            Object status = snapshot.get("trialStatus");
            if (status != null) sb.append("- Trial status: ").append(status).append("\n");
            Object totalAes = snapshot.get("totalAdverseEvents");
            if (totalAes != null) sb.append("- Total adverse events: ").append(totalAes).append("\n");
            Object grade3Plus = snapshot.get("grade3PlusCount");
            if (grade3Plus != null) sb.append("- Grade 3+ adverse events: ").append(grade3Plus).append("\n");
            Object hasGrade5 = snapshot.get("hasGrade5");
            if (hasGrade5 != null) sb.append("- Grade 5 events present: ").append(hasGrade5).append("\n");
            Object priorAmendments = snapshot.get("priorAmendmentCount");
            if (priorAmendments != null) sb.append("- Prior amendments: ").append(priorAmendments).append("\n");
        }
        return sb.toString();
    }


}
