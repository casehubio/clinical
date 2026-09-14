package io.casehub.clinical.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClinicalAgentSupport {

    private static final Logger LOG = Logger.getLogger(ClinicalAgentSupport.class);
    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*\\n(\\{.*?})\\s*\\n```", Pattern.DOTALL);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_MODEL = "sonnet";

    private final AgentProvider agentProvider;
    private final ObjectMapper objectMapper;
    private final io.casehub.clinical.service.ClinicalCascadeBroadcaster cascadeBroadcaster;

    @Inject
    public ClinicalAgentSupport(AgentProvider agentProvider, ObjectMapper objectMapper,
                                io.casehub.clinical.service.ClinicalCascadeBroadcaster cascadeBroadcaster) {
        this.agentProvider = agentProvider;
        this.objectMapper = objectMapper;
        this.cascadeBroadcaster = cascadeBroadcaster;
    }

    public <T> ClinicalAgentResult<T> invoke(ClinicalAgentRequest<T> request) {
        InvocationMetrics metrics = null;
        if (request.cascadeAeId() != null) {
            cascadeBroadcaster.agentReasoning(request.cascadeAeId(), request.configKey());
        }
        try {
            String model = resolveModel(request.configKey());
            Duration timeout = resolveTimeout(request.configKey());
            AgentSessionConfig config = new AgentSessionConfig(
                    request.systemPrompt(), request.userPrompt(),
                    List.of(), timeout, request.correlationId(), model);

            List<AgentEvent> events = agentProvider.invoke(config)
                    .collect().asList()
                    .await().atMost(timeout.plusSeconds(5));

            String rawText = events.stream()
                    .filter(AgentEvent.TextDelta.class::isInstance)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect(Collectors.joining());

            metrics = events.stream()
                    .filter(AgentEvent.InvocationComplete.class::isInstance)
                    .map(e -> mapMetrics(model, (AgentEvent.InvocationComplete) e))
                    .findFirst().orElse(null);

            if (metrics != null && metrics.isError()) {
                LOG.warnf("ClinicalAgentSupport[%s]: InvocationComplete.isError=true — using fallback", request.configKey());
                if (request.cascadeAeId() != null) {
                    cascadeBroadcaster.agentResult(request.cascadeAeId(), request.configKey(), false);
                }
                return ClinicalAgentResult.fallback(request.fallbackValue(), "InvocationComplete.isError=true", metrics);
            }

            if (rawText == null || rawText.isBlank()) {
                LOG.warnf("ClinicalAgentSupport[%s]: empty response — using fallback", request.configKey());
                if (request.cascadeAeId() != null) {
                    cascadeBroadcaster.agentResult(request.cascadeAeId(), request.configKey(), false);
                }
                return ClinicalAgentResult.fallback(request.fallbackValue(), "empty response", metrics);
            }

            String json = extractJson(rawText);
            T parsed = objectMapper.readValue(json, request.responseClass());
            if (request.cascadeAeId() != null) {
                cascadeBroadcaster.agentResult(request.cascadeAeId(), request.configKey(), true);
            }
            return ClinicalAgentResult.success(parsed, rawText, metrics);

        } catch (Exception e) {
            LOG.errorf(e, "ClinicalAgentSupport[%s]: invocation failed — using fallback", request.configKey());
            if (request.cascadeAeId() != null) {
                cascadeBroadcaster.agentResult(request.cascadeAeId(), request.configKey(), false);
            }
            return ClinicalAgentResult.fallback(request.fallbackValue(), e.getMessage(), metrics);
        }
    }

    String extractJson(String text) {
        Matcher m = JSON_FENCE.matcher(text);
        if (m.find()) return m.group(1);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }

    private String resolveModel(String configKey) {
        try {
            return org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getOptionalValue("casehub.clinical.agent." + configKey + ".model", String.class)
                    .orElse(DEFAULT_MODEL);
        } catch (Exception e) {
            return DEFAULT_MODEL;
        }
    }

    private Duration resolveTimeout(String configKey) {
        try {
            String value = org.eclipse.microprofile.config.ConfigProvider.getConfig()
                    .getOptionalValue("casehub.clinical.agent." + configKey + ".timeout", String.class)
                    .orElse(null);
            return value != null ? Duration.parse(value) : DEFAULT_TIMEOUT;
        } catch (Exception e) {
            return DEFAULT_TIMEOUT;
        }
    }

    private InvocationMetrics mapMetrics(String model, AgentEvent.InvocationComplete ic) {
        return new InvocationMetrics(model, ic.inputTokens(), ic.outputTokens(),
                ic.thinkingTokens(), ic.cacheReadTokens(), ic.cacheWriteTokens(),
                ic.totalCostUsd(), ic.durationMs(), ic.apiDurationMs(),
                ic.sessionId(), ic.numTurns(), ic.isError());
    }
}
