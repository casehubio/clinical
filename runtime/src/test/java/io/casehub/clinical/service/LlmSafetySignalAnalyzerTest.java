package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.clinical.api.spi.SignalAnalysis;
import io.casehub.clinical.api.spi.TrialSafetyContext;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmSafetySignalAnalyzerTest {

    private AgentProvider agentProvider;
    private LlmSafetySignalAnalyzer analyzer;

    @BeforeEach
    void setup() {
        agentProvider = mock(AgentProvider.class);
        @SuppressWarnings("unchecked") jakarta.enterprise.event.Event<io.casehub.clinical.agent.ModelSelectionEvent> mockModelEvent = mock(jakarta.enterprise.event.Event.class);
        var support = new ClinicalAgentSupport(agentProvider, new ObjectMapper(), mock(ClinicalCascadeBroadcaster.class), mock(io.casehub.platform.api.model.ModelRegistry.class), mockModelEvent);
        analyzer = new LlmSafetySignalAnalyzer(support);
    }

    private TrialSafetyContext sampleContext() {
        UUID trialId = UUID.randomUUID();
        var signal = new TrialSafetyContext.DetectedSignalSummary(
                "GRADE_THRESHOLD", List.of("site-1", "site-2", "site-3"),
                "3 of 5 sites show Grade 3+ AE rate above 10%", "GRADE_3", "NAUSEA");
        return new TrialSafetyContext(trialId, "PHASE_II",
                Map.of(), List.of(signal));
    }

    @Test
    void validJsonResponse_parsedToSignalAnalysis() {
        String json = """
                {"signals":[{"signalType":"GRADE_THRESHOLD","severity":"MODERATE","affectedSites":["site-1","site-2"],\
                "narrative":"Elevated nausea rate across sites","recommendedAction":"Continue monitoring"}],\
                "overallAssessment":"Moderate concern — monitoring recommended","additionalPatterns":["Temporal clustering in week 3"]}""";
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta(json),
                new AgentEvent.InvocationComplete(300, 150, 0, 0, 0, 0.008, 3000L, 2800L, "sess-1", 1, false)));

        SignalAnalysis result = analyzer.analyze(sampleContext());

        assertNotNull(result);
        assertEquals(1, result.signals().size());
        assertEquals("GRADE_THRESHOLD", result.signals().get(0).signalType());
        assertEquals("MODERATE", result.signals().get(0).severity());
        assertNotNull(result.overallAssessment());
        assertEquals(1, result.additionalPatterns().size());
    }

    @Test
    void malformedJson_returnsFlagForReviewFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("Cannot analyze these signals"),
                new AgentEvent.InvocationComplete(100, 50, 0, 0, 0, null, 1000L, 900L, "sess-2", 1, false)));

        SignalAnalysis result = analyzer.analyze(sampleContext());

        assertNotNull(result);
        assertTrue(result.overallAssessment().contains("LLM analysis unavailable"));
        assertFalse(result.signals().isEmpty());
    }

    @Test
    void emptyResponse_returnsFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().empty());

        SignalAnalysis result = analyzer.analyze(sampleContext());

        assertNotNull(result);
        assertTrue(result.overallAssessment().contains("LLM analysis unavailable"));
    }

    @Test
    void llmException_returnsFallback() {
        when(agentProvider.invoke(any())).thenReturn(
                Multi.createFrom().failure(new RuntimeException("timeout")));

        SignalAnalysis result = analyzer.analyze(sampleContext());

        assertNotNull(result);
        assertTrue(result.overallAssessment().contains("LLM analysis unavailable"));
    }
}
