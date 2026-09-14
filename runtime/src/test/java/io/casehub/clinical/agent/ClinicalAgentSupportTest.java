package io.casehub.clinical.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.enterprise.event.Event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClinicalAgentSupportTest {

    private AgentProvider agentProvider;
    private ClinicalAgentSupport support;

    record TestResponse(String value, int count) {}

    @BeforeEach
    void setup() {
        agentProvider = mock(AgentProvider.class);
        @SuppressWarnings("unchecked")
        Event<ModelSelectionEvent> mockEvent = mock(Event.class);
        support = new ClinicalAgentSupport(agentProvider, new ObjectMapper(), mock(io.casehub.clinical.service.ClinicalCascadeBroadcaster.class), mock(io.casehub.platform.api.model.ModelRegistry.class), mockEvent);
    }

    @Test
    void validJsonResponse_parsedCorrectly() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"value\":\"hello\",\"count\":42}"),
                new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.001, 500L, 400L, "sess-1", 1, false)));
        var request = new ClinicalAgentRequest<>("system", "user", TestResponse.class,
                new TestResponse("fallback", 0), "test", "corr-1", null);
        var result = support.invoke(request);
        assertFalse(result.fallbackUsed());
        assertEquals("hello", result.response().value());
        assertEquals(42, result.response().count());
        assertNotNull(result.metrics());
        assertEquals(10, result.metrics().inputTokens());
        assertEquals(20, result.metrics().outputTokens());
        assertFalse(result.metrics().isError());
    }

    @Test
    void markdownFencedJson_extractedCorrectly() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("Here is the result:\n```json\n{\"value\":\"hi\",\"count\":1}\n```\n"),
                new AgentEvent.InvocationComplete(5, 10, 0, 0, 0, null, 200L, 150L, "sess-2", 1, false)));
        var request = new ClinicalAgentRequest<>("system", "user", TestResponse.class,
                new TestResponse("fallback", 0), "test", "corr-2", null);
        var result = support.invoke(request);
        assertFalse(result.fallbackUsed());
        assertEquals("hi", result.response().value());
    }

    @Test
    void malformedJson_returnsFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("not valid json at all"),
                new AgentEvent.InvocationComplete(5, 10, 0, 0, 0, null, 200L, 150L, "sess-3", 1, false)));
        var request = new ClinicalAgentRequest<>("system", "user", TestResponse.class,
                new TestResponse("fallback", 0), "test", "corr-3", null);
        var result = support.invoke(request);
        assertTrue(result.fallbackUsed());
        assertEquals("fallback", result.response().value());
    }

    @Test
    void emptyResponse_returnsFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().empty());
        var request = new ClinicalAgentRequest<>("system", "user", TestResponse.class,
                new TestResponse("fallback", 0), "test", "corr-4", null);
        var result = support.invoke(request);
        assertTrue(result.fallbackUsed());
    }

    @Test
    void invocationCompleteIsError_returnsFallback() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"value\":\"ok\",\"count\":1}"),
                new AgentEvent.InvocationComplete(5, 10, 0, 0, 0, null, 200L, 150L, "sess-5", 1, true)));
        var request = new ClinicalAgentRequest<>("system", "user", TestResponse.class,
                new TestResponse("fallback", 0), "test", "corr-5", null);
        var result = support.invoke(request);
        assertTrue(result.fallbackUsed());
        assertEquals("InvocationComplete.isError=true", result.failureReason());
    }

    @Test
    void exceptionDuringInvocation_returnsFallback() {
        when(agentProvider.invoke(any())).thenReturn(
                Multi.createFrom().failure(new RuntimeException("connection failed")));
        var request = new ClinicalAgentRequest<>("system", "user", TestResponse.class,
                new TestResponse("fallback", 0), "test", "corr-6", null);
        var result = support.invoke(request);
        assertTrue(result.fallbackUsed());
        assertNotNull(result.failureReason());
    }

    @Test
    void modelPassedToAgentSessionConfig() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"value\":\"ok\",\"count\":1}"),
                new AgentEvent.InvocationComplete(5, 10, 0, 0, 0, null, 200L, 150L, "sess-7", 1, false)));
        var request = new ClinicalAgentRequest<>("system", "user", TestResponse.class,
                new TestResponse("fallback", 0), "safety", "corr-7", null);
        support.invoke(request);
        ArgumentCaptor<AgentSessionConfig> captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).invoke(captor.capture());
        assertEquals("system", captor.getValue().systemPrompt());
        assertEquals("user", captor.getValue().userPrompt());
        assertEquals("corr-7", captor.getValue().correlationId());
    }

    @Test
    void extractJson_plainJson() {
        assertEquals("{\"a\":1}", support.extractJson("{\"a\":1}"));
    }

    @Test
    void extractJson_fencedJson() {
        String input = "Some text\n```json\n{\"a\":1}\n```\nMore text";
        assertEquals("{\"a\":1}", support.extractJson(input));
    }

    @Test
    void extractJson_embeddedInText() {
        String input = "The result is {\"a\":1} and that's it.";
        assertEquals("{\"a\":1}", support.extractJson(input));
    }

    @Test
    void metricsModelSourcedFromConfig() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"value\":\"ok\",\"count\":1}"),
                new AgentEvent.InvocationComplete(5, 10, 0, 0, 0, 0.002, 200L, 150L, "sess-8", 1, false)));
        var request = new ClinicalAgentRequest<>("system", "user", TestResponse.class,
                new TestResponse("fallback", 0), "test", "corr-8", null);
        var result = support.invoke(request);
        assertNotNull(result.metrics());
        assertEquals("sonnet", result.metrics().model());
    }
}
