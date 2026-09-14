package io.casehub.clinical.service;

import io.casehub.clinical.agent.ClinicalAgentRequest;
import io.casehub.clinical.agent.ClinicalAgentResult;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.clinical.api.spi.SafetySignalAnalyzer;
import io.casehub.clinical.api.spi.SignalAnalysis;
import io.casehub.clinical.api.spi.TrialSafetyContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class LlmSafetySignalAnalyzer implements SafetySignalAnalyzer {

    private static final Logger LOG = Logger.getLogger(LlmSafetySignalAnalyzer.class);

    static final String SYSTEM_PROMPT = """
            You are an independent data safety monitoring analyst. You receive a summary of \
            adverse events across trial sites with rule-based signal detections.

            Assess whether detected signals represent genuine safety concerns. Identify subtler \
            patterns the rules may have missed: temporal clustering, dose-response relationships, \
            organ-system crossover.

            Provide narrative assessment suitable for DSMB review. You are advisory — the DSMB \
            makes all binding decisions.

            Respond with JSON only:
            {"signals":[{"signalType":"GRADE_THRESHOLD","severity":"MODERATE",\
            "affectedSites":["site-1"],"narrative":"...","recommendedAction":"..."}],\
            "overallAssessment":"one paragraph","additionalPatterns":["pattern description"]}
            """;

    private final ClinicalAgentSupport agentSupport;

    @Inject
    public LlmSafetySignalAnalyzer(ClinicalAgentSupport agentSupport) {
        this.agentSupport = agentSupport;
    }

    @Override
    public SignalAnalysis analyze(TrialSafetyContext context) {
        SignalAnalysis fallback = buildFallback(context);
        String userPrompt = buildUserPrompt(context);

        var request = new ClinicalAgentRequest<>(
                SYSTEM_PROMPT, userPrompt, SignalAnalysis.class,
                fallback, "dsmb",
                context.trialId() != null ? context.trialId().toString() : null, null);

        ClinicalAgentResult<SignalAnalysis> result = agentSupport.invoke(request);

        if (result.fallbackUsed()) {
            LOG.warnf("LlmSafetySignalAnalyzer: fallback used — %s", result.failureReason());
            return fallback;
        }

        return result.response();
    }

    private SignalAnalysis buildFallback(TrialSafetyContext context) {
        List<SignalAnalysis.AnalyzedSignal> signals = context.detectedSignals().stream()
                .map(s -> new SignalAnalysis.AnalyzedSignal(
                        s.signalType(), "UNKNOWN", s.affectedSiteIds(),
                        s.summary(), "FLAG_FOR_REVIEW — LLM analysis unavailable"))
                .toList();
        return new SignalAnalysis(signals,
                "LLM analysis unavailable — all rule-based signals flagged for DSMB human review",
                List.of());
    }

    private String buildUserPrompt(TrialSafetyContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Trial Context\n");
        sb.append("- Trial ID: ").append(context.trialId()).append("\n");
        sb.append("- Phase: ").append(context.trialPhase()).append("\n");
        sb.append("- Sites reporting data: ").append(context.siteData().size()).append("\n\n");

        if (!context.detectedSignals().isEmpty()) {
            sb.append("## Rule-Based Detected Signals\n\n");
            for (var signal : context.detectedSignals()) {
                sb.append("### ").append(signal.signalType()).append("\n");
                sb.append("- Summary: ").append(signal.summary()).append("\n");
                sb.append("- Dominant grade: ").append(signal.dominantGrade()).append("\n");
                sb.append("- Dominant event type: ").append(signal.dominantEventType()).append("\n");
                sb.append("- Affected sites: ").append(signal.affectedSiteIds().size()).append("\n\n");
            }
        }

        if (!context.siteData().isEmpty()) {
            sb.append("## Per-Site AE Summaries\n\n");
            for (var entry : context.siteData().entrySet()) {
                sb.append("### Site ").append(entry.getKey()).append("\n");
                for (var summary : entry.getValue()) {
                    sb.append("- ").append(summary.eventType()).append(" Grade ").append(summary.grade())
                            .append(": ").append(summary.count()).append(" events\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
