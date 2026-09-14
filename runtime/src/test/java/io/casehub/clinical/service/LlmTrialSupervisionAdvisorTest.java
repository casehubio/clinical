package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.clinical.api.spi.SupervisionAssessment;
import io.casehub.clinical.api.spi.TrialSupervisionContext;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmTrialSupervisionAdvisorTest {

    private AgentProvider agentProvider;
    private LlmTrialSupervisionAdvisor advisor;

    @BeforeEach
    void setup() {
        agentProvider = mock(AgentProvider.class);
        @SuppressWarnings("unchecked") jakarta.enterprise.event.Event<io.casehub.clinical.agent.ModelSelectionEvent> mockModelEvent = mock(jakarta.enterprise.event.Event.class);
        var support = new ClinicalAgentSupport(agentProvider, new ObjectMapper(), mock(ClinicalCascadeBroadcaster.class), mock(io.casehub.platform.api.model.ModelRegistry.class), mockModelEvent);
        advisor = new LlmTrialSupervisionAdvisor(support);
    }

    private TrialSupervisionContext sampleContext() {
        return new TrialSupervisionContext(UUID.randomUUID(), "PHASE_II",
                Map.of("trialId", UUID.randomUUID().toString(), "totalSites", 5));
    }

    @Test
    void validJsonResponse_parsedToAssessment() {
        String json = """
                {"overallHealth":"WATCH","findings":[{"findingType":"enrollment_lag","affectedSites":["site-3"],\
                "severity":"MODERATE","narrative":"Site 3 at 40% of target","recommendedAction":"Increase recruitment"}],\
                "summary":"One site lagging enrollment target"}""";
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta(json),
                new AgentEvent.InvocationComplete(300, 150, 0, 0, 0, 0.008, 3000L, 2800L, "sess-1", 1, false)));

        SupervisionAssessment result = advisor.assess(sampleContext());

        assertNotNull(result);
        assertEquals("WATCH", result.overallHealth());
        assertEquals(1, result.findings().size());
        assertEquals("enrollment_lag", result.findings().get(0).findingType());
    }

    @Test
    void malformedJson_returnsFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("I need more data"),
                new AgentEvent.InvocationComplete(100, 50, 0, 0, 0, null, 1000L, 900L, "sess-2", 1, false)));

        SupervisionAssessment result = advisor.assess(sampleContext());

        assertNotNull(result);
        assertEquals("REVIEW_REQUIRED", result.overallHealth());
    }

    @Test
    void emptyResponse_returnsFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().empty());

        SupervisionAssessment result = advisor.assess(sampleContext());

        assertNotNull(result);
        assertEquals("REVIEW_REQUIRED", result.overallHealth());
    }

    @Test
    void llmException_returnsFallback() {
        when(agentProvider.invoke(any())).thenReturn(
                Multi.createFrom().failure(new RuntimeException("timeout")));

        SupervisionAssessment result = advisor.assess(sampleContext());

        assertEquals("REVIEW_REQUIRED", result.overallHealth());
    }
}
