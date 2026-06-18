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
import static org.mockito.Mockito.*;

@QuarkusTest
class ProtocolAmendmentListenerTest {

    @Inject ProtocolAmendmentListener listener;
    @InjectMock CaseInstanceRepository caseInstanceRepository;
    @InjectMock ProtocolAmendmentLedgerWriter ledgerWriter;

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
    }

    private CaseLifecycleEvent goalReached(UUID caseId, String tenancyId) {
        return new CaseLifecycleEvent(caseId, tenancyId, "CompleteCase",
            "GoalReached", "RUNNING", "system", "system", null);
    }

    private void mockInstance(UUID caseId, String advisorRec) {
        CaseContext ctx = mock(CaseContext.class);
        when(ctx.getPath("amendmentId")).thenReturn(amendmentId.toString());
        when(ctx.getPath("advisorRecommendation")).thenReturn(advisorRec);
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(eq(caseId), any()))
            .thenReturn(Uni.createFrom().item(instance));
    }

    @Test
    void proceed_sets_APPROVED_and_COMPLETED_and_non_null_recommendation() {
        mockInstance(caseId, "PROCEED");
        listener.onCaseLifecycle(goalReached(caseId, "default"));

        ProtocolAmendment a = ProtocolAmendment.findById(amendmentId);
        assertThat(a.status).isEqualTo(ProtocolAmendmentStatus.APPROVED);
        assertThat(a.amendmentCaseStatus).isEqualTo(AmendmentCaseStatus.COMPLETED);
        assertThat(a.supervisorRecommendation).isNotNull();
        verify(ledgerWriter).writeResolutionEntry(any());
    }

    @Test
    void halt_sets_HALTED_and_COMPLETED() {
        mockInstance(caseId, "HALT");
        listener.onCaseLifecycle(goalReached(caseId, "default"));

        ProtocolAmendment a = ProtocolAmendment.findById(amendmentId);
        assertThat(a.status).isEqualTo(ProtocolAmendmentStatus.HALTED);
        assertThat(a.amendmentCaseStatus).isEqualTo(AmendmentCaseStatus.COMPLETED);
        assertThat(a.supervisorRecommendation).isEqualTo(io.casehub.clinical.api.spi.AmendmentRecommendation.HALT);
    }

    @Test
    void refer_to_dsmb_sets_SUPERVISED_and_COMPLETED() {
        mockInstance(caseId, "REFER_TO_DSMB");
        listener.onCaseLifecycle(goalReached(caseId, "default"));

        ProtocolAmendment a = ProtocolAmendment.findById(amendmentId);
        assertThat(a.status).isEqualTo(ProtocolAmendmentStatus.SUPERVISED);
        assertThat(a.amendmentCaseStatus).isEqualTo(AmendmentCaseStatus.COMPLETED);
        assertThat(a.supervisorRecommendation).isEqualTo(io.casehub.clinical.api.spi.AmendmentRecommendation.REFER_TO_DSMB);
    }

    @Test
    void redelivery_skipped_when_supervisorRecommendation_already_set() {
        // First delivery
        mockInstance(caseId, "PROCEED");
        listener.onCaseLifecycle(goalReached(caseId, "default"));

        // Second delivery (re-delivery)
        reset(ledgerWriter);
        listener.onCaseLifecycle(goalReached(caseId, "default"));
        verifyNoInteractions(ledgerWriter);
    }

    @Test
    void non_amendment_case_skipped_when_amendmentId_absent_from_context() {
        CaseContext ctx = mock(CaseContext.class);
        when(ctx.getPath("amendmentId")).thenReturn(null);
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getCaseContext()).thenReturn(ctx);
        when(caseInstanceRepository.findByUuid(eq(caseId), any()))
            .thenReturn(Uni.createFrom().item(instance));

        listener.onCaseLifecycle(goalReached(caseId, "default"));
        verifyNoInteractions(ledgerWriter);
    }

    @Test
    void writes_resolution_ledger_entry_exactly_once() {
        mockInstance(caseId, "PROCEED");
        listener.onCaseLifecycle(goalReached(caseId, "default"));
        verify(ledgerWriter, times(1)).writeResolutionEntry(any());
    }
}
