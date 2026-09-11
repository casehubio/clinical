package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.spi.TrialSupervisionAdvisor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class TrialSupervisionIntegrationTest {

    @Inject TrialSupervisionAdvisor advisor;
    @Inject FixedCurrentPrincipal principal;
    @InjectMock AgentProvider agentProvider;

    @BeforeEach
    void setup() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"overallHealth\":\"HEALTHY\",\"findings\":[],\"summary\":\"All good\"}"),
                new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.001, 500L, 400L, "sess-1", 1, false)));
    }

    @Test
    void cdiDisplacement_llmImplIsActive() {
        assertInstanceOf(LlmTrialSupervisionAdvisor.class, advisor);
    }
}
