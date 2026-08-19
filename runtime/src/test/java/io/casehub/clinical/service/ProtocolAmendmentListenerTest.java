package io.casehub.clinical.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
class ProtocolAmendmentListenerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject ProtocolAmendmentListener listener;
    @InjectMock ProtocolAmendmentStatusUpdater statusUpdater;

    UUID amendmentId;
    UUID caseId;

    @BeforeEach
    @Transactional
    void setup() {
        amendmentId = UUID.randomUUID();
        caseId = UUID.randomUUID();

        ProtocolAmendment a = new ProtocolAmendment();
        a.id = amendmentId;
        a.trialId = UUID.randomUUID();
        a.proposedChange = "Dose escalation v2";
        a.status = ProtocolAmendmentStatus.PROPOSED;
        a.amendmentCaseStatus = AmendmentCaseStatus.REQUESTED;
        a.tenantId = "default";
        a.proposedAt = Instant.now();
        a.persist();

        doNothing().when(statusUpdater).applyRecommendation(any(), any());
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
