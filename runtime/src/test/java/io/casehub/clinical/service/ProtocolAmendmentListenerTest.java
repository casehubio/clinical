package io.casehub.clinical.service;

import io.casehub.api.context.CaseContext;
import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class ProtocolAmendmentListenerTest {

    @Inject ProtocolAmendmentListener listener;
    @InjectMock CaseInstanceRepository caseInstanceRepository;
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

        // Stub statusUpdater as no-op — updater tests cover write logic
        doNothing().when(statusUpdater).applyRecommendation(any(), any());
    }

    private CaseLifecycleEvent goalReached(UUID caseId, String tenancyId) {
        return CaseLifecycleEvent.of(caseId, tenancyId, "CompleteCase",
            "GoalReached", "RUNNING", "system", "system", null);
    }

    private void mockInstance(UUID caseId, String advisorRec) {
        CaseContext ctx = mock(CaseContext.class);
        when(ctx.getPath("amendmentId")).thenReturn(amendmentId.toString());
        when(ctx.getPath("advisorRecommendation")).thenReturn(advisorRec);
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(eq(caseId), any()))
            .thenReturn(instance);
    }

    @Test
    void proceed_delegates_to_updater() {
        mockInstance(caseId, "PROCEED");
        listener.onCaseLifecycle(goalReached(caseId, "default"));

        verify(statusUpdater).applyRecommendation(eq(amendmentId), eq("PROCEED"));
    }

    @Test
    void halt_delegates_to_updater() {
        mockInstance(caseId, "HALT");
        listener.onCaseLifecycle(goalReached(caseId, "default"));

        verify(statusUpdater).applyRecommendation(eq(amendmentId), eq("HALT"));
    }

    @Test
    void refer_to_dsmb_delegates_to_updater() {
        mockInstance(caseId, "REFER_TO_DSMB");
        listener.onCaseLifecycle(goalReached(caseId, "default"));

        verify(statusUpdater).applyRecommendation(eq(amendmentId), eq("REFER_TO_DSMB"));
    }

    @Test
    void non_amendment_case_skipped_when_amendmentId_absent_from_context() {
        CaseContext ctx = mock(CaseContext.class);
        when(ctx.getPath("amendmentId")).thenReturn(null);
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(eq(caseId), any()))
            .thenReturn(instance);

        listener.onCaseLifecycle(goalReached(caseId, "default"));
        verifyNoInteractions(statusUpdater);
    }

    @Test
    void delegates_exactly_once_per_event() {
        mockInstance(caseId, "PROCEED");
        listener.onCaseLifecycle(goalReached(caseId, "default"));
        verify(statusUpdater, times(1)).applyRecommendation(eq(amendmentId), eq("PROCEED"));
    }
}
