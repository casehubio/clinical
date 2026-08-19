package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.memory.ClinicalMemoryService;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * @QuarkusTest integration test — verifies ClinicalMemoryService.storeAeOutcome()
 * is called after a successful AE escalation completion, and skipped when tenantId
 * is absent from the case context.
 */
@QuarkusTest
class AeEscalationListenerMemoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject AeEscalationListener listener;
    @InjectMock ClinicalMemoryService memoryService;
    @InjectMock AeEscalationLedgerWriter ledgerWriter;
    @InjectMock AeStatusUpdater statusUpdater;
    @InjectMock Event<AeEscalationCompletedEvent> completedEvents;

    @BeforeEach
    void stubDefaults() {
        doNothing().when(memoryService).storeAeOutcome(any(), any(), any(), any(), anyBoolean(), any());
        when(completedEvents.fireAsync(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
    }

    @Test
    void storeAeOutcome_called_with_correct_args_on_completion() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        ObjectNode snapshot = buildSnapshot(aeId, enrollmentId, siteId, "GRADE_3",
                "REVIEWED", "completed", "test-tenant");

        when(statusUpdater.markCompleted(aeId)).thenReturn(true);

        listener.onCaseLifecycle(goalReached(caseId, snapshot));

        ArgumentCaptor<UUID> aeCaptor      = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> enrollCaptor  = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);

        verify(memoryService).storeAeOutcome(aeCaptor.capture(), enrollCaptor.capture(),
            any(), any(), anyBoolean(), tenantCaptor.capture());

        assertThat(aeCaptor.getValue()).isEqualTo(aeId);
        assertThat(enrollCaptor.getValue()).isEqualTo(enrollmentId);
        assertThat(tenantCaptor.getValue()).isEqualTo("test-tenant");
    }

    @Test
    void storeAeOutcome_skipped_when_tenantId_absent() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        ObjectNode snapshot = buildSnapshot(aeId, enrollmentId, siteId, "GRADE_3",
                "REVIEWED", "completed", null);

        when(statusUpdater.markCompleted(aeId)).thenReturn(true);

        listener.onCaseLifecycle(goalReached(caseId, snapshot));

        verify(memoryService, never()).storeAeOutcome(any(), any(), any(), any(), anyBoolean(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ObjectNode buildSnapshot(UUID aeId, UUID enrollmentId, UUID siteId,
                                             String grade, String safetyReviewOutcome,
                                             String dsmbEscalation, String tenantId) {
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
        return snapshot;
    }

    private static CaseLifecycleEvent goalReached(UUID caseId, ObjectNode snapshot) {
        return new CaseLifecycleEvent(
            caseId, "default", "CompleteCase", "GoalReached", "RUNNING",
            "system", "system", null, null, null, snapshot, null, null);
    }
}
