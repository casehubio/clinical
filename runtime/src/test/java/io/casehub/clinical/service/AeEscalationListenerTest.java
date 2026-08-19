package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.memory.ClinicalMemoryService;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AeEscalationListenerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock AeEscalationLedgerWriter ledgerWriter;
    @Mock AeStatusUpdater statusUpdater;
    @Mock Event<AeEscalationCompletedEvent> completedEvents;
    @Mock ClinicalMemoryService memoryService;
    @InjectMocks AeEscalationListener listener;

    @Test
    void completed_event_carries_siteId_from_case_context() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        ObjectNode snapshot = buildSnapshot(aeId, enrollmentId, siteId, "GRADE_4",
                "REVIEWED", "completed", "test-tenant", null);

        when(statusUpdater.markCompleted(aeId)).thenReturn(true);
        when(completedEvents.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        listener.onCaseLifecycle(new CaseLifecycleEvent(
                caseId, "default", "CompleteCase", "CaseCompleted", "COMPLETED",
                "system", "system", null, null, null, snapshot, null, null));

        ArgumentCaptor<AeEscalationCompletedEvent> captor =
                ArgumentCaptor.forClass(AeEscalationCompletedEvent.class);
        verify(completedEvents).fireAsync(captor.capture());

        AeEscalationCompletedEvent fired = captor.getValue();
        assertThat(fired.aeId()).isEqualTo(aeId);
        assertThat(fired.grade()).isEqualTo(CtcaeGrade.GRADE_4);
        assertThat(fired.siteId()).isEqualTo(siteId);
    }

    @Test
    void completed_event_carries_unexpected_from_case_context() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        ObjectNode snapshot = buildSnapshot(aeId, enrollmentId, siteId, "GRADE_5",
                "REVIEWED", "completed", "test-tenant", true);

        when(statusUpdater.markCompleted(aeId)).thenReturn(true);
        when(completedEvents.fireAsync(any())).thenReturn(CompletableFuture.completedFuture(null));

        listener.onCaseLifecycle(new CaseLifecycleEvent(
                caseId, "default", "CompleteCase", "CaseCompleted", "COMPLETED",
                "system", "system", null, null, null, snapshot, null, null));

        ArgumentCaptor<AeEscalationCompletedEvent> captor =
                ArgumentCaptor.forClass(AeEscalationCompletedEvent.class);
        verify(completedEvents).fireAsync(captor.capture());

        assertThat(captor.getValue().unexpected()).isTrue();
    }

    @Test
    void non_completed_events_are_ignored() {
        listener.onCaseLifecycle(new CaseLifecycleEvent(
                UUID.randomUUID(), "default", "StartCase", "CaseStarted", "RUNNING",
                "system", "system", null, null, null, null, null, null));

        verifyNoInteractions(statusUpdater);
        verifyNoInteractions(completedEvents);
    }

    @Test
    void markCompleted_true_but_enrollmentId_null_skips_ledger_write() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();

        ObjectNode snapshot = MAPPER.createObjectNode();
        snapshot.put("aeId", aeId.toString());

        when(statusUpdater.markCompleted(aeId)).thenReturn(true);

        assertThatCode(() -> listener.onCaseLifecycle(goalReachedEvent(caseId, snapshot)))
            .doesNotThrowAnyException();

        verifyNoInteractions(ledgerWriter);
        verifyNoInteractions(completedEvents);
    }

    @Test
    void idempotency_guard_skips_ledger_write_on_duplicate_goal_reached() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();

        ObjectNode snapshot = MAPPER.createObjectNode();
        snapshot.put("aeId", aeId.toString());

        when(statusUpdater.markCompleted(aeId)).thenReturn(false);

        listener.onCaseLifecycle(goalReachedEvent(caseId, snapshot));

        verifyNoInteractions(ledgerWriter);
        verifyNoInteractions(completedEvents);
    }

    @Test
    void writeCompletionEntry_throws_writes_observer_failure_entry() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        ObjectNode snapshot = buildSnapshot(aeId, enrollmentId, null, "GRADE_3",
                null, null, null, null);

        when(statusUpdater.markCompleted(aeId)).thenReturn(true);
        doThrow(new RuntimeException("ledger write failed"))
            .when(ledgerWriter).writeCompletionEntry(any(), any(), any(), any(), anyBoolean(), any());

        assertThatCode(() -> listener.onCaseLifecycle(goalReachedEvent(caseId, snapshot)))
            .doesNotThrowAnyException();

        verify(ledgerWriter).writeObserverFailureEntry(eq(aeId), eq(enrollmentId), eq(CtcaeGrade.GRADE_3));
    }

    @Test
    void writeCompletionEntry_and_fallback_both_throw_does_not_propagate() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        ObjectNode snapshot = buildSnapshot(aeId, enrollmentId, null, "GRADE_4",
                null, null, null, null);

        when(statusUpdater.markCompleted(aeId)).thenReturn(true);
        doThrow(new RuntimeException("ledger write failed"))
            .when(ledgerWriter).writeCompletionEntry(any(), any(), any(), any(), anyBoolean(), any());
        doThrow(new RuntimeException("fallback write failed"))
            .when(ledgerWriter).writeObserverFailureEntry(any(), any(), any());

        assertThatCode(() -> listener.onCaseLifecycle(goalReachedEvent(caseId, snapshot)))
            .doesNotThrowAnyException();
    }

    @Test
    void fireAsync_throws_after_ledger_written_no_failure_entry() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        ObjectNode snapshot = buildSnapshot(aeId, enrollmentId, null, "GRADE_3",
                null, null, null, null);

        when(statusUpdater.markCompleted(aeId)).thenReturn(true);
        doThrow(new RuntimeException("fireAsync failed"))
            .when(completedEvents).fireAsync(any());

        assertThatCode(() -> listener.onCaseLifecycle(goalReachedEvent(caseId, snapshot)))
            .doesNotThrowAnyException();

        verify(ledgerWriter, never()).writeObserverFailureEntry(any(), any(), any());
    }

    // --- helpers ---

    private static CaseLifecycleEvent goalReachedEvent(UUID caseId, ObjectNode snapshot) {
        return new CaseLifecycleEvent(
                caseId, "default", "CompleteCase", "GoalReached", "RUNNING",
                "system", "system", null, null, null, snapshot, null, null);
    }

    private static ObjectNode buildSnapshot(UUID aeId, UUID enrollmentId, UUID siteId,
                                             String grade, String safetyReviewOutcome,
                                             String dsmbEscalation, String tenantId,
                                             Boolean unexpected) {
        ObjectNode snapshot = MAPPER.createObjectNode();
        if (aeId != null) snapshot.put("aeId", aeId.toString());
        if (enrollmentId != null) snapshot.put("enrollmentId", enrollmentId.toString());
        if (siteId != null) snapshot.put("siteId", siteId.toString());
        if (grade != null) snapshot.put("grade", grade);
        if (safetyReviewOutcome != null) {
            snapshot.putObject("safetyReview").put("outcome", safetyReviewOutcome);
        }
        if (dsmbEscalation != null) snapshot.put("dsmbEscalation", dsmbEscalation);
        if (tenantId != null) snapshot.put("tenantId", tenantId);
        if (unexpected != null) snapshot.put("unexpected", unexpected);
        return snapshot;
    }
}
