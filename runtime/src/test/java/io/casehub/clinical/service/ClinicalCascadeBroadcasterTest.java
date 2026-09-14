package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.AeEscalationFailedEvent;
import io.casehub.clinical.api.AeEscalationStartedEvent;
import io.casehub.clinical.api.CascadeEvent;
import io.casehub.clinical.api.CascadeStepStatus;
import io.casehub.clinical.api.CascadeStepType;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.pages.push.EventBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ClinicalCascadeBroadcasterTest {

    private EventBroadcaster eventBroadcaster;
    private ClinicalCascadeBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        eventBroadcaster = mock(EventBroadcaster.class);
        broadcaster = new ClinicalCascadeBroadcaster(eventBroadcaster);
    }

    @Test
    void onAeReportedBroadcastsTwoSteps() {
        UUID aeId = UUID.randomUUID();
        var event = new AdverseEventReportedEvent(aeId, UUID.randomUUID(), UUID.randomUUID(),
            CtcaeGrade.GRADE_4, Instant.now(), "test-tenant");
        broadcaster.onAeReported(event);

        String expectedTopic = "clinical:ae:" + aeId + ":cascade";
        var captor = ArgumentCaptor.forClass(CascadeEvent.class);
        verify(eventBroadcaster, times(2)).broadcast(eq(expectedTopic), captor.capture());
        assertThat(captor.getAllValues().get(0).step()).isEqualTo(CascadeStepType.AE_REPORTED);
        assertThat(captor.getAllValues().get(1).step()).isEqualTo(CascadeStepType.SLA_ASSIGNED);
    }

    @Test
    void onEscalationStartedBroadcastsStep() {
        UUID aeId = UUID.randomUUID();
        var event = new AeEscalationStartedEvent(aeId, UUID.randomUUID(), CtcaeGrade.GRADE_4, "test-tenant");
        broadcaster.onEscalationStarted(event);

        var captor = ArgumentCaptor.forClass(CascadeEvent.class);
        verify(eventBroadcaster).broadcast(eq("clinical:ae:" + aeId + ":cascade"), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(CascadeStepType.ESCALATION_CASE_STARTED);
        assertThat(captor.getValue().status()).isEqualTo(CascadeStepStatus.COMPLETED);
    }

    @Test
    void onEscalationFailedBroadcastsFailedStep() {
        UUID aeId = UUID.randomUUID();
        var event = new AeEscalationFailedEvent(aeId, CtcaeGrade.GRADE_4, "test-tenant", "startCase() threw");
        broadcaster.onEscalationFailed(event);

        var captor = ArgumentCaptor.forClass(CascadeEvent.class);
        verify(eventBroadcaster).broadcast(eq("clinical:ae:" + aeId + ":cascade"), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(CascadeStepType.ESCALATION_CASE_STARTED);
        assertThat(captor.getValue().status()).isEqualTo(CascadeStepStatus.FAILED);
    }

    @Test
    void safetyOfficerNotifiedUsesCorrectTopic() {
        UUID aeId = UUID.randomUUID();
        broadcaster.safetyOfficerNotified(aeId);

        var captor = ArgumentCaptor.forClass(CascadeEvent.class);
        verify(eventBroadcaster).broadcast(eq("clinical:ae:" + aeId + ":cascade"), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(CascadeStepType.SAFETY_OFFICER_NOTIFIED);
        assertThat(captor.getValue().status()).isEqualTo(CascadeStepStatus.COMPLETED);
    }

    @Test
    void agentReasoningIsActive() {
        UUID aeId = UUID.randomUUID();
        broadcaster.agentReasoning(aeId, "safety");

        var captor = ArgumentCaptor.forClass(CascadeEvent.class);
        verify(eventBroadcaster).broadcast(eq("clinical:ae:" + aeId + ":cascade"), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(CascadeStepType.AGENT_REASONING);
        assertThat(captor.getValue().status()).isEqualTo(CascadeStepStatus.ACTIVE);
    }

    @Test
    void agentResultCompleted() {
        UUID aeId = UUID.randomUUID();
        broadcaster.agentResult(aeId, "safety", true);

        var captor = ArgumentCaptor.forClass(CascadeEvent.class);
        verify(eventBroadcaster).broadcast(eq("clinical:ae:" + aeId + ":cascade"), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(CascadeStepType.AGENT_RESULT);
        assertThat(captor.getValue().status()).isEqualTo(CascadeStepStatus.COMPLETED);
    }

    @Test
    void agentResultFailed() {
        UUID aeId = UUID.randomUUID();
        broadcaster.agentResult(aeId, "safety", false);

        var captor = ArgumentCaptor.forClass(CascadeEvent.class);
        verify(eventBroadcaster).broadcast(eq("clinical:ae:" + aeId + ":cascade"), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(CascadeStepStatus.FAILED);
    }

    @Test
    void broadcastGrade12CascadeEmitsThreeSteps() {
        UUID aeId = UUID.randomUUID();
        broadcaster.broadcastGrade12Cascade(aeId, CtcaeGrade.GRADE_1, Instant.now());

        verify(eventBroadcaster, times(3)).broadcast(eq("clinical:ae:" + aeId + ":cascade"), any(CascadeEvent.class));
    }

    @Test
    void regulatorySubmissionStarted() {
        UUID aeId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        broadcaster.regulatorySubmissionStarted(aeId, caseId);

        var captor = ArgumentCaptor.forClass(CascadeEvent.class);
        verify(eventBroadcaster).broadcast(eq("clinical:ae:" + aeId + ":cascade"), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(CascadeStepType.REGULATORY_SUBMISSION_STARTED);
    }

    @Test
    void ledgerSealed() {
        UUID aeId = UUID.randomUUID();
        broadcaster.ledgerSealed(aeId, "abc123");

        var captor = ArgumentCaptor.forClass(CascadeEvent.class);
        verify(eventBroadcaster).broadcast(eq("clinical:ae:" + aeId + ":cascade"), captor.capture());
        assertThat(captor.getValue().step()).isEqualTo(CascadeStepType.LEDGER_SEALED);
        assertThat(captor.getValue().data()).containsEntry("digest", "abc123");
    }
}
