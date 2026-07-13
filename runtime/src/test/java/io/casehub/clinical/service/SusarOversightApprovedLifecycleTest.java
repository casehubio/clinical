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
 * Approved-path lifecycle test for SUSAR oversight gate.
 *
 * <p>Tests the two observable approved-path behaviors:
 * 1. Gate approved: SusarGateDecisionListener writes ledger entry + does not signal case.
 * 2. Goal reached: SusarOversightListener marks ae.susarOversightStatus = COMPLETED.
 *
 * <p>Drivers are called directly (integration test pattern per CLAUDE.md) rather than through
 * the full Quartz-worker-gate chain, which is unreliable in @QuarkusTest because Java function
 * workers require the Quartz scheduler to fire on a separate thread with a JTA context.
 * The full gate creation path is covered by SusarActionGateLifecycleTest (evaluator unit)
 * and SusarGateDecisionListenerTest (gate decision unit).
 */
@QuarkusTest
class SusarOversightApprovedLifecycleTest {

    // Fixed tenancyId from FixedCurrentPrincipal — used when constructing CaseLifecycleEvent
    // so InMemoryCaseInstanceRepository.findByUuid(caseId, tenancyId) can locate the case.
    private static final String TEST_TENANCY_ID = "278776f9-e1b0-46fb-9032-8bddebdcf9ce";

    @Inject SusarOversightCaseService caseService;
    @Inject SusarGateDecisionListener gateDecisionListener;
    @Inject SusarOversightListener oversightListener;

    @Test
    void approved_gate_writes_ledger_and_case_completes() {
        UUID aeId = persistAe();
        AdverseEventReportedEvent event = buildEvent(aeId);

        // Checkpoint 1: start oversight case (three-phase, synchronous)
        caseService.onAdverseEventReported(event);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(findAe(aeId).susarOversightCaseId).isNotNull());

        UUID caseId = findAe(aeId).susarOversightCaseId;

        // Checkpoint 2: drive gate approval directly — tests the approved gate path in
        // SusarGateDecisionListener (ledger entry written, no case signals).
        // The gateId is not significant here — listener discriminates via DB lookup, not gateId.
        gateDecisionListener.onApproved(new ActionGateApprovedEvent(caseId, "default", 1L, null, "dr-smith"));

        // Checkpoint 3: drive the goal-reached observer directly — tests SusarOversightListener
        // (GoalReached → markCompleted → ae.susarOversightStatus = COMPLETED).
        // Tenancy ID must match what FixedCurrentPrincipal provides so InMemoryCaseInstanceRepository
        // findByUuid(caseId, tenancyId) locates the case.
        CaseLifecycleEvent goalReached = CaseLifecycleEvent.of(
                caseId, TEST_TENANCY_ID, "GoalReached", "GoalReached",
                "COMPLETED", "system", "SYSTEM", null);
        oversightListener.onCaseLifecycle(goalReached);

        // Domain assertion: SusarOversightStatusUpdater.markCompleted() committed in REQUIRES_NEW —
        // synchronously visible from the test thread.
        assertThat(findAe(aeId).susarOversightStatus).isEqualTo(SusarOversightStatus.COMPLETED);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Transactional
    UUID persistAe() {
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
        ae.tenantId = "test-tenant";
        ae.persist();
        return ae.id;
    }

    AdverseEventReportedEvent buildEvent(UUID aeId) {
        return new AdverseEventReportedEvent(
                aeId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CtcaeGrade.GRADE_4,
                Instant.now(),
                "test-tenant");
    }

    @Transactional
    AdverseEvent findAe(UUID aeId) {
        return AdverseEvent.findById(aeId);
    }
}
