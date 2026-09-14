package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.worker.api.WorkerOutcome;
import io.casehub.worker.api.WorkerResult;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmSusarCriteriaEvaluatorTest {

    private AgentProvider agentProvider;
    private LlmSusarCriteriaEvaluator evaluator;

    @BeforeEach
    void setup() {
        agentProvider = mock(AgentProvider.class);
        var support = new ClinicalAgentSupport(agentProvider, new ObjectMapper(), mock(ClinicalCascadeBroadcaster.class), mock(io.casehub.platform.api.model.ModelRegistry.class));
        evaluator = new LlmSusarCriteriaEvaluator(support);
    }

    @Test
    void susarRequired_returnsWorkerResultWithPlannedAction() {
        String json = """
                {"susarRequired":true,"causalityAssessment":"probable","expectednessAssessment":"unexpected","reasoning":"temporal relationship, dose-response","confidence":0.85}""";
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta(json),
                new AgentEvent.InvocationComplete(200, 100, 0, 0, 0, 0.005, 2000L, 1800L, "sess-1", 1, false)));

        Map<String, Object> context = Map.of("aeId", UUID.randomUUID().toString());
        WorkerResult<Map<String, Object>> result = evaluator.apply(context);

        assertNotNull(result);
        assertEquals(true, result.output().get("susarRequired"));
        assertEquals(true, result.output().get("susarAssessmentComplete"));
        assertEquals("probable", result.output().get("causalityAssessment"));
        assertInstanceOf(WorkerOutcome.Success.class, result.outcome());
        assertNotNull(((WorkerOutcome.Success<?>) result.outcome()).plannedAction());
    }

    @Test
    void susarNotRequired_returnsWorkerResultWithoutPlannedAction() {
        String json = """
                {"susarRequired":false,"causalityAssessment":"unrelated","expectednessAssessment":"expected","reasoning":"known side effect","confidence":0.92}""";
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta(json),
                new AgentEvent.InvocationComplete(200, 100, 0, 0, 0, 0.005, 2000L, 1800L, "sess-2", 1, false)));

        Map<String, Object> context = Map.of("aeId", UUID.randomUUID().toString());
        WorkerResult<Map<String, Object>> result = evaluator.apply(context);

        assertNotNull(result);
        assertEquals(false, result.output().get("susarRequired"));
        assertInstanceOf(WorkerOutcome.Success.class, result.outcome());
        assertNull(((WorkerOutcome.Success<?>) result.outcome()).plannedAction());
    }

    @Test
    void llmFailure_returnsFallbackSusarRequired() {
        when(agentProvider.invoke(any())).thenReturn(
                Multi.createFrom().failure(new RuntimeException("connection failed")));

        Map<String, Object> context = Map.of("aeId", UUID.randomUUID().toString());
        WorkerResult<Map<String, Object>> result = evaluator.apply(context);

        assertNotNull(result);
        assertEquals(true, result.output().get("susarRequired"));
        assertInstanceOf(WorkerOutcome.Success.class, result.outcome());
        assertNotNull(((WorkerOutcome.Success<?>) result.outcome()).plannedAction());
    }

    @Test
    void malformedJson_returnsFallbackSusarRequired() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("I cannot assess this AE properly"),
                new AgentEvent.InvocationComplete(100, 50, 0, 0, 0, null, 1000L, 900L, "sess-4", 1, false)));

        Map<String, Object> context = Map.of("aeId", UUID.randomUUID().toString());
        WorkerResult<Map<String, Object>> result = evaluator.apply(context);

        assertNotNull(result);
        assertEquals(true, result.output().get("susarRequired"));
    }

    @Test
    void missingAeId_returnsFallbackSusarRequired() {
        Map<String, Object> context = Map.of();
        WorkerResult<Map<String, Object>> result = evaluator.apply(context);

        assertNotNull(result);
        assertEquals(true, result.output().get("susarRequired"));
    }

    @Test
    void invocationCompleteIsError_returnsFallbackSusarRequired() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"susarRequired\":false}"),
                new AgentEvent.InvocationComplete(100, 50, 0, 0, 0, null, 1000L, 900L, "sess-err", 1, true)));

        Map<String, Object> context = Map.of("aeId", UUID.randomUUID().toString());
        WorkerResult<Map<String, Object>> result = evaluator.apply(context);

        assertEquals(true, result.output().get("susarRequired"));
    }
}
