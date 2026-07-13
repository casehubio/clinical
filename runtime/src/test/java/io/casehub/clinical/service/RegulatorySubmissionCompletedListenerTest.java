package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RegulatorySubmissionCompletedListenerTest {

    @Inject RegulatorySubmissionCompletedListener listener;
    @InjectMock RegulatorySubmissionLedgerWriter ledgerWriter;

    @BeforeEach
    void reset() {
        // @InjectMock replaces the bean — no-op stubs (verify-only tests)
    }

    @Test
    void goalReached_for_regulatory_submission_case_sets_status_filed() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = persistAe(caseId, RegulatorySubmissionStatus.PENDING);

        listener.onCaseLifecycleEvent(event("GoalReached", caseId));

        assertThat(findAe(aeId).regulatorySubmissionStatus)
                .isEqualTo(RegulatorySubmissionStatus.FILED);
        verify(ledgerWriter).writeFiledEntry(any());
    }

    @Test
    void caseCompleted_also_sets_status_filed() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = persistAe(caseId, RegulatorySubmissionStatus.PENDING);

        listener.onCaseLifecycleEvent(event("CaseCompleted", caseId));

        assertThat(findAe(aeId).regulatorySubmissionStatus)
                .isEqualTo(RegulatorySubmissionStatus.FILED);
        verify(ledgerWriter).writeFiledEntry(any());
    }

    @Test
    void unrelated_case_id_is_ignored() {
        listener.onCaseLifecycleEvent(event("GoalReached", UUID.randomUUID()));
        verify(ledgerWriter, never()).writeFiledEntry(any());
    }

    @Test
    void double_goalReached_is_idempotent() {
        UUID caseId = UUID.randomUUID();
        persistAe(caseId, RegulatorySubmissionStatus.PENDING);

        listener.onCaseLifecycleEvent(event("GoalReached", caseId));
        listener.onCaseLifecycleEvent(event("GoalReached", caseId));

        verify(ledgerWriter, times(1)).writeFiledEntry(any());
    }

    @Test
    void deadline_missed_status_is_protected_by_pending_guard() {
        UUID caseId = UUID.randomUUID();
        UUID aeId = persistAe(caseId, RegulatorySubmissionStatus.DEADLINE_MISSED);

        listener.onCaseLifecycleEvent(event("GoalReached", caseId));

        assertThat(findAe(aeId).regulatorySubmissionStatus)
                .isEqualTo(RegulatorySubmissionStatus.DEADLINE_MISSED);
        verify(ledgerWriter, never()).writeFiledEntry(any());
    }

    @Test
    void non_goalReached_eventType_is_ignored() {
        UUID caseId = UUID.randomUUID();
        persistAe(caseId, RegulatorySubmissionStatus.PENDING);

        listener.onCaseLifecycleEvent(event("CaseStarted", caseId));

        verify(ledgerWriter, never()).writeFiledEntry(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    @Transactional
    UUID persistAe(UUID caseId, RegulatorySubmissionStatus status) {
        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = CtcaeGrade.GRADE_3;
        ae.unexpected = true;
        ae.suspected = true;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.tenantId = "test-tenant";
        ae.regulatorySubmissionCaseId = caseId;
        ae.regulatorySubmissionStatus = status;
        ae.persist();
        return ae.id;
    }

    @Transactional
    AdverseEvent findAe(UUID aeId) {
        return AdverseEvent.findById(aeId);
    }

    private CaseLifecycleEvent event(String eventType, UUID caseId) {
        return CaseLifecycleEvent.of(
                caseId, "test-tenant", "SomeCommand", eventType,
                null, null, null, null);
    }
}
