package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.internal.event.ActionGateApprovedEvent;
import io.casehub.engine.common.spi.event.CaseLifecycleEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for SusarOversightCaseService three-phase lifecycle.
 *
 * <p>Full oversight path uses direct listener invocation (GE-20260614-b97659 Option 1)
 * — the Quartz-gated function worker is not reliable in @QuarkusTest.
 */
@QuarkusTest
class SusarOversightLifecycleTest {

    private static final String TEST_TENANCY_ID = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";

    @Inject SusarOversightCaseService service;
    @Inject SusarGateDecisionListener gateDecisionListener;
    @Inject SusarOversightListener oversightListener;

    @Test
    void grade4_unexpected_suspected_starts_oversight_case() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, true, true);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_4);

        service.onAdverseEventReported(event);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(findAe(aeId).susarOversightCaseId).isNotNull());
        UUID caseId = findAe(aeId).susarOversightCaseId;

        // Drive gate approval and case completion directly (GE-20260614-b97659 Option 1)
        gateDecisionListener.onApproved(new ActionGateApprovedEvent(caseId, "default", 1L, null, "dr-smith"));
        oversightListener.onCaseLifecycle(CaseLifecycleEvent.of(
                caseId, TEST_TENANCY_ID, "GoalReached", "GoalReached",
                "COMPLETED", "system", "SYSTEM", null));

        assertThat(findAe(aeId).susarOversightStatus).isEqualTo(SusarOversightStatus.COMPLETED);
    }

    @Test
    void grade2_does_not_start_oversight_case() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_2, false, true);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_2);

        service.onAdverseEventReported(event);

        AdverseEvent ae = findAe(aeId);
        assertThat(ae.susarOversightStatus).isEqualTo(SusarOversightStatus.NONE);
        assertThat(ae.susarOversightCaseId).isNull();
    }

    @Test
    void idempotency_guard_prevents_double_start() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, true, true);
        setStatus(aeId, SusarOversightStatus.REQUESTED);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_4);

        service.onAdverseEventReported(event);

        assertThat(findAe(aeId).susarOversightCaseId).isNull();
    }

    @Transactional
    UUID persistAe(CtcaeGrade grade, boolean unexpected, boolean suspected) {
        UUID aeId = UUID.randomUUID();
        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = grade;
        ae.unexpected = unexpected;
        ae.suspected = suspected;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.tenantId = TEST_TENANCY_ID;
        ae.persist();
        return aeId;
    }

    @Transactional
    void setStatus(UUID aeId, SusarOversightStatus status) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        ae.susarOversightStatus = status;
    }

    @Transactional
    AdverseEvent findAe(UUID aeId) {
        return AdverseEvent.findById(aeId);
    }

    AdverseEventReportedEvent buildEvent(UUID aeId, CtcaeGrade grade) {
        return new AdverseEventReportedEvent(
                aeId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                grade,
                Instant.now(),
                TEST_TENANCY_ID);
    }
}
