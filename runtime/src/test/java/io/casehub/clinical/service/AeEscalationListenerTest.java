package io.casehub.clinical.service;

import io.casehub.api.context.CaseContext;
import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.engine.internal.event.CaseLifecycleEvent;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AeEscalationListenerTest {

    @Mock CaseInstanceRepository caseInstanceRepository;
    @Mock AeEscalationLedgerWriter ledgerWriter;
    @Mock Event<AeEscalationCompletedEvent> completedEvents;
    @InjectMocks AeEscalationListener listener;

    @Test
    void completed_event_carries_siteId_from_case_context() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        CaseContext ctx = mock(CaseContext.class);
        when(ctx.getPath("aeId")).thenReturn(aeId.toString());
        when(ctx.getPath("enrollmentId")).thenReturn(enrollmentId.toString());
        when(ctx.getPath("grade")).thenReturn("GRADE_4");
        when(ctx.getPath("siteId")).thenReturn(siteId.toString());
        when(ctx.getPath("safetyReview")).thenReturn(Map.of(AeEscalationListener.OUTCOME_KEY, "REVIEWED"));
        when(ctx.getPath("dsmbEscalation")).thenReturn("completed");

        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(caseId)).thenReturn(Uni.createFrom().item(instance));
        when(completedEvents.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        listener.onCaseLifecycle(new CaseLifecycleEvent(
                caseId, "CompleteCase", "CaseCompleted", "COMPLETED", "system", "system"));

        ArgumentCaptor<AeEscalationCompletedEvent> captor =
                ArgumentCaptor.forClass(AeEscalationCompletedEvent.class);
        verify(completedEvents).fireAsync(captor.capture());

        AeEscalationCompletedEvent fired = captor.getValue();
        assertThat(fired.aeId()).isEqualTo(aeId);
        assertThat(fired.grade()).isEqualTo(CtcaeGrade.GRADE_4);
        assertThat(fired.siteId()).isEqualTo(siteId);
    }

    @Test
    void non_completed_events_are_ignored() {
        listener.onCaseLifecycle(new CaseLifecycleEvent(
                UUID.randomUUID(), "StartCase", "CaseStarted", "RUNNING", "system", "system"));

        verifyNoInteractions(caseInstanceRepository);
        verifyNoInteractions(completedEvents);
    }
}
