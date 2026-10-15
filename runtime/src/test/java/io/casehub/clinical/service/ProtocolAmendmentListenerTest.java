package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProtocolAmendmentListenerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    ProtocolAmendmentListener listener;
    @Mock ProtocolAmendmentStatusUpdater statusUpdater;


    UUID amendmentId;
    UUID caseId;

    @BeforeEach
    void setup() {
        amendmentId = UUID.randomUUID();
        caseId = UUID.randomUUID();
        listener = new ProtocolAmendmentListener();
        listener.statusUpdater = statusUpdater;
    }

    private CaseLifecycleEvent goalReached(UUID caseId, ObjectNode snapshot) {
        return new CaseLifecycleEvent(caseId, "default", "CompleteCase",
            "GoalReached", "RUNNING", "system", "system", null,
            null, null, snapshot, null, null);
    }

    private ObjectNode buildSnapshot(String advisorRec) {
        ObjectNode snapshot = MAPPER.createObjectNode();
        snapshot.put("amendmentId", amendmentId.toString());
        if (advisorRec != null) snapshot.put("advisorRecommendation", advisorRec);
        return snapshot;
    }

    @Test
    void proceed_delegates_to_updater() {
        listener.onCaseLifecycle(goalReached(caseId, buildSnapshot("PROCEED")));
        verify(statusUpdater).applyRecommendation(eq(amendmentId), eq("PROCEED"));
    }

    @Test
    void halt_delegates_to_updater() {
        listener.onCaseLifecycle(goalReached(caseId, buildSnapshot("HALT")));
        verify(statusUpdater).applyRecommendation(eq(amendmentId), eq("HALT"));
    }

    @Test
    void refer_to_dsmb_delegates_to_updater() {
        listener.onCaseLifecycle(goalReached(caseId, buildSnapshot("REFER_TO_DSMB")));
        verify(statusUpdater).applyRecommendation(eq(amendmentId), eq("REFER_TO_DSMB"));
    }

    @Test
    void non_amendment_case_skipped_when_amendmentId_absent_from_context() {
        ObjectNode snapshot = MAPPER.createObjectNode();
        listener.onCaseLifecycle(goalReached(caseId, snapshot));
        verifyNoInteractions(statusUpdater);
    }

    @Test
    void delegates_exactly_once_per_event() {
        listener.onCaseLifecycle(goalReached(caseId, buildSnapshot("PROCEED")));
        verify(statusUpdater, times(1)).applyRecommendation(eq(amendmentId), eq("PROCEED"));
    }
}
