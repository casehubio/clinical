package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentAdvisor;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.stream.Collectors;

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

    private final AgentProvider agentProvider;

    @Inject
    public LlmProtocolAmendmentAdvisor(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public AmendmentRecommendation advise(ProtocolAmendmentContext context) {
        try {
            String userPrompt = buildUserPrompt(context);
            AgentSessionConfig config = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt);
            String response = agentProvider.invoke(config)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().atMost(java.time.Duration.ofSeconds(30));
            if (response == null || response.isBlank()) {
                LOG.warn("LlmProtocolAmendmentAdvisor: empty response from AgentProvider — defaulting to PROCEED");
                return AmendmentRecommendation.PROCEED;
            }
            return parseRecommendation(response);
        } catch (Exception e) {
            LOG.errorf(e, "LlmProtocolAmendmentAdvisor: invocation failed — defaulting to PROCEED");
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

    private AmendmentRecommendation parseRecommendation(String response) {
        String value = extractJsonValue(response, "recommendation");
        if (value == null) {
            LOG.warnf("LlmProtocolAmendmentAdvisor: could not parse recommendation from response: %s", response);
            return AmendmentRecommendation.PROCEED;
        }
        try {
            return AmendmentRecommendation.valueOf(value);
        } catch (IllegalArgumentException e) {
            LOG.warnf("LlmProtocolAmendmentAdvisor: unknown recommendation '%s' — defaulting to PROCEED", value);
            return AmendmentRecommendation.PROCEED;
        }
    }

    static String extractJsonValue(String json, String key) {
        if (json == null) return null;
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
        if (colonIdx < 0) return null;
        int firstQuote = json.indexOf('"', colonIdx + 1);
        if (firstQuote < 0) return null;
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;
        return json.substring(firstQuote + 1, secondQuote);
    }
}
