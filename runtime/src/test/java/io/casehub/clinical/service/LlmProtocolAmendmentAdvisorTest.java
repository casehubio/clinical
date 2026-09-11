package io.casehub.clinical.service;

import io.casehub.clinical.agent.ClinicalAgentRequest;
import io.casehub.clinical.agent.ClinicalAgentResult;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.clinical.api.spi.ProtocolAmendmentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmProtocolAmendmentAdvisorTest {

    private ClinicalAgentSupport agentSupport;
    private LlmProtocolAmendmentAdvisor advisor;

    @BeforeEach
    void setUp() {
        agentSupport = mock(ClinicalAgentSupport.class);
        advisor = new LlmProtocolAmendmentAdvisor(agentSupport);
    }

    private ProtocolAmendmentContext context(Map<String, Object> snapshot) {
        return new ProtocolAmendmentContext(UUID.randomUUID(), UUID.randomUUID(),
                "Add imaging endpoint to protocol", snapshot);
    }

    @SuppressWarnings("unchecked")
    private void stubAgentResponse(String recommendation, String reasoning) {
        var response = new LlmProtocolAmendmentAdvisor.AmendmentResponse(recommendation, reasoning);
        when(agentSupport.invoke(any(ClinicalAgentRequest.class)))
                .thenReturn(ClinicalAgentResult.success(response, "{}", null));
    }

    @SuppressWarnings("unchecked")
    private void stubFallback() {
        var fallback = new LlmProtocolAmendmentAdvisor.AmendmentResponse("PROCEED", "fallback");
        when(agentSupport.invoke(any(ClinicalAgentRequest.class)))
                .thenReturn(ClinicalAgentResult.fallback(fallback, "test failure", null));
    }

    @Test
    void proceed_response_parsed_correctly() {
        stubAgentResponse("PROCEED", "Administrative change only");
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.PROCEED);
    }

    @Test
    void refer_to_dsmb_response_parsed_correctly() {
        stubAgentResponse("REFER_TO_DSMB", "Elevated Grade 4 rate");
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.REFER_TO_DSMB);
    }

    @Test
    void halt_response_parsed_correctly() {
        stubAgentResponse("HALT", "Grade 5 events present");
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.HALT);
    }

    @Test
    void fallback_returns_proceed() {
        stubFallback();
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.PROCEED);
    }

    @Test
    void unknown_recommendation_falls_back_to_proceed() {
        stubAgentResponse("SUSPEND", "Unknown action");
        assertThat(advisor.advise(context(Map.of()))).isEqualTo(AmendmentRecommendation.PROCEED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void prompt_contains_proposed_change() {
        stubAgentResponse("PROCEED", "ok");
        advisor.advise(context(Map.of("trialPhase", "PHASE_III", "totalAdverseEvents", 5L)));

        ArgumentCaptor<ClinicalAgentRequest<LlmProtocolAmendmentAdvisor.AmendmentResponse>> captor =
                ArgumentCaptor.forClass(ClinicalAgentRequest.class);
        verify(agentSupport).invoke(captor.capture());
        String userPrompt = captor.getValue().userPrompt();
        assertThat(userPrompt).contains("Add imaging endpoint to protocol");
        assertThat(userPrompt).contains("PHASE_III");
        assertThat(userPrompt).contains("5");
        assertThat(captor.getValue().configKey()).isEqualTo("amendment");
    }
}
