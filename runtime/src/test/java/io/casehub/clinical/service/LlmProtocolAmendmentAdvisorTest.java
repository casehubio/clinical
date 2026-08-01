package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmProtocolAmendmentAdvisorTest {

    private AgentProvider agentProvider;
    private LlmProtocolAmendmentAdvisor advisor;

    @BeforeEach
    void setUp() {
        agentProvider = mock(AgentProvider.class);
        advisor = new LlmProtocolAmendmentAdvisor(agentProvider);
    }

    private ProtocolAmendmentContext context(Map<String, Object> snapshot) {
        return new ProtocolAmendmentContext(UUID.randomUUID(), UUID.randomUUID(),
                "Add imaging endpoint to protocol", snapshot);
    }

    private void stubLlmResponse(String text) {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(text)));
    }

    @Test
    void proceed_response_parsed_correctly() {
        stubLlmResponse("{\"recommendation\": \"PROCEED\", \"reasoning\": \"Administrative change only\"}");
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.PROCEED);
    }

    @Test
    void refer_to_dsmb_response_parsed_correctly() {
        stubLlmResponse("{\"recommendation\": \"REFER_TO_DSMB\", \"reasoning\": \"Elevated Grade 4 rate\"}");
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.REFER_TO_DSMB);
    }

    @Test
    void halt_response_parsed_correctly() {
        stubLlmResponse("{\"recommendation\": \"HALT\", \"reasoning\": \"Grade 5 events present\"}");
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.HALT);
    }

    @Test
    void empty_response_falls_back_to_proceed() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().empty());
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.PROCEED);
    }

    @Test
    void malformed_json_falls_back_to_proceed() {
        stubLlmResponse("this is not json at all");
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.PROCEED);
    }

    @Test
    void unknown_recommendation_falls_back_to_proceed() {
        stubLlmResponse("{\"recommendation\": \"SUSPEND\", \"reasoning\": \"Unknown action\"}");
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.PROCEED);
    }

    @Test
    void invocation_exception_falls_back_to_proceed() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM unavailable")));
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.PROCEED);
    }

    @Test
    void prompt_contains_proposed_change() {
        stubLlmResponse("{\"recommendation\": \"PROCEED\", \"reasoning\": \"ok\"}");
        advisor.advise(context(Map.of("trialPhase", "PHASE_III", "totalAdverseEvents", 5L)));

        var configCaptor = org.mockito.ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(configCaptor.capture());
        String userPrompt = configCaptor.getValue().userPrompt();
        assertThat(userPrompt).contains("Add imaging endpoint to protocol");
        assertThat(userPrompt).contains("PHASE_III");
        assertThat(userPrompt).contains("5");
    }

    @Test
    void multi_chunk_response_concatenated() {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().items(
                        new AgentEvent.TextDelta("{\"recommendation\": \"HA"),
                        new AgentEvent.TextDelta("LT\", \"reasoning\": \"critical\"}"))
                );
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.HALT);
    }
}
