package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.entity.ConcomitantMedication;
import io.casehub.clinical.entity.LabResult;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.VitalSign;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmEligibilityCriteriaEvaluatorTest {

    private AgentProvider agentProvider;
    private LlmEligibilityCriteriaEvaluator evaluator;

    @BeforeEach
    void setup() {
        agentProvider = mock(AgentProvider.class);
        var support = new ClinicalAgentSupport(agentProvider, new ObjectMapper());
        evaluator = new LlmEligibilityCriteriaEvaluator(support);
    }

    @Test
    void validJsonResponse_parsedToCriterionResults() {
        String json = """
                {"criteria":[
                    {"criterionId":"criterion-0","met":true,"marginal":false,"reasoning":"Age 45 meets 18-65 range"},
                    {"criterionId":"criterion-1","met":false,"marginal":true,"reasoning":"ECOG 2 borderline for 0-1 requirement"}
                ]}""";
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta(json),
                new AgentEvent.InvocationComplete(100, 50, 0, 0, 0, 0.002, 1000L, 900L, "sess-1", 1, false)));

        List<CriterionResult> results = evaluator.evaluate(
                UUID.randomUUID(), "default",
                List.of("Age 18-65", "ECOG performance status 0-1"));

        assertEquals(2, results.size());
        assertEquals("criterion-0", results.get(0).id());
        assertTrue(results.get(0).met());
        assertFalse(results.get(0).marginal());
        assertEquals("criterion-1", results.get(1).id());
        assertFalse(results.get(1).met());
        assertTrue(results.get(1).marginal());
    }

    @Test
    void malformedJson_returnsAllMarginalFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("I cannot evaluate this patient"),
                new AgentEvent.InvocationComplete(50, 30, 0, 0, 0, null, 500L, 400L, "sess-2", 1, false)));

        List<CriterionResult> results = evaluator.evaluate(
                UUID.randomUUID(), "default",
                List.of("Age 18-65", "No prior chemotherapy"));

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(CriterionResult::marginal));
        assertTrue(results.stream().noneMatch(CriterionResult::met));
    }

    @Test
    void emptyResponse_returnsAllMarginalFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().empty());

        List<CriterionResult> results = evaluator.evaluate(
                UUID.randomUUID(), "default",
                List.of("Hemoglobin >= 10 g/dL"));

        assertEquals(1, results.size());
        assertTrue(results.get(0).marginal());
    }

    @Test
    void llmException_returnsAllMarginalFallback() {
        when(agentProvider.invoke(any())).thenReturn(
                Multi.createFrom().failure(new RuntimeException("connection timeout")));

        List<CriterionResult> results = evaluator.evaluate(
                UUID.randomUUID(), "default",
                List.of("Age 18-65", "ECOG 0-1", "No active infection"));

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(CriterionResult::marginal));
    }

    @Test
    void invocationCompleteIsError_returnsAllMarginalFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"criteria\":[{\"criterionId\":\"c-0\",\"met\":true,\"marginal\":false,\"reasoning\":\"ok\"}]}"),
                new AgentEvent.InvocationComplete(50, 30, 0, 0, 0, null, 500L, 400L, "sess-err", 1, true)));

        List<CriterionResult> results = evaluator.evaluate(
                UUID.randomUUID(), "default",
                List.of("Age 18-65"));

        assertEquals(1, results.size());
        assertTrue(results.get(0).marginal());
    }

    @Test
    void usesEligibilityConfigKey() {
        String json = "{\"criteria\":[{\"criterionId\":\"c-0\",\"met\":true,\"marginal\":false,\"reasoning\":\"ok\"}]}";
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta(json),
                new AgentEvent.InvocationComplete(50, 30, 0, 0, 0, null, 500L, 400L, "sess-3", 1, false)));

        evaluator.evaluate(UUID.randomUUID(), "default", List.of("Age 18-65"));

        var captor = ArgumentCaptor.forClass(io.casehub.platform.agent.AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertNotNull(captor.getValue());
    }
}
