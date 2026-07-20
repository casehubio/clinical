package io.casehub.clinical.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.internal.event.ActionGateApprovedEvent;
import io.casehub.engine.common.internal.event.ActionGateExpiredEvent;
import io.casehub.engine.common.internal.event.ActionGateRejectedEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class SusarGateDecisionListenerTest {

    @Inject SusarGateDecisionListener listener;
    @InjectMock SusarDecisionLedgerWriter ledgerWriter;
    @InjectMock ClinicalSusarOversightCaseHub caseHub;

    @BeforeEach
    void resetMocks() {
        reset(ledgerWriter, caseHub);
    }

    @Test
    @Transactional
    void approved_writes_ledger_and_does_not_signal() {
        UUID caseId = UUID.randomUUID();
        AdverseEvent ae = persistAe(caseId);

        listener.onApproved(new ActionGateApprovedEvent(caseId, "default", 1L, null, "dr-smith", null));

        verify(ledgerWriter).writeEntry(
                Mockito.argThat(a -> a.id.equals(ae.id)),
                eq("APPROVED"),
                any(Instant.class),
                eq("dr-smith"));
        verifyNoInteractions(caseHub);
    }

    @Test
    @Transactional
    void rejected_signals_case_and_writes_ledger() {
        UUID caseId = UUID.randomUUID();
        AdverseEvent ae = persistAe(caseId);

        listener.onRejected(new ActionGateRejectedEvent(caseId, "default", 1L, null, "dr-jones"));

        verify(caseHub).signal(caseId, "susarAssessmentComplete", true);
        verify(caseHub).signal(caseId, "susarRequired", false);
        verify(ledgerWriter).writeEntry(
                Mockito.argThat(a -> a.id.equals(ae.id)),
                eq("REJECTED"),
                any(Instant.class),
                eq("dr-jones"));
    }

    @Test
    @Transactional
    void expired_signals_case_and_writes_with_system_actor() {
        UUID caseId = UUID.randomUUID();
        AdverseEvent ae = persistAe(caseId);

        listener.onExpired(new ActionGateExpiredEvent(caseId, "default", 1L));

        verify(caseHub).signal(caseId, "susarAssessmentComplete", true);
        verify(caseHub).signal(caseId, "susarRequired", false);
        verify(ledgerWriter).writeEntry(
                Mockito.argThat(a -> a.id.equals(ae.id)),
                eq("EXPIRED"),
                any(Instant.class),
                eq("clinical-service"));
    }

    @Test
    void unknown_case_id_is_silently_ignored() {
        UUID unknownCaseId = UUID.randomUUID();

        listener.onRejected(new ActionGateRejectedEvent(unknownCaseId, "default", 1L, null, "dr-jones"));

        verifyNoInteractions(ledgerWriter, caseHub);
    }

    @Transactional
    AdverseEvent persistAe(UUID susarOversightCaseId) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_4;
        ae.unexpected = true;
        ae.suspected = true;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.tenantId = "default";
        ae.susarOversightStatus = SusarOversightStatus.REQUESTED;
        ae.susarOversightCaseId = susarOversightCaseId;
        ae.persist();
        return ae;
    }
}
