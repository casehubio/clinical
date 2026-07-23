package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.worker.api.WorkerOutcome;
import io.casehub.worker.api.WorkerResult;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.ClinicalActionType;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.entity.AdverseEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Integration test — SusarCriteriaEvaluator entity loading via @Transactional.
 *
 * <p>Verifies the evaluator correctly loads AdverseEvent from DB and evaluates criteria.
 * The full engine gate lifecycle through SusarOversightCaseService is covered
 * by SusarOversightLifecycleTest and SusarOversightApprovedLifecycleTest.
 */
@QuarkusTest
class SusarActionGateLifecycleTest {

    @Inject SusarCriteriaEvaluator evaluator;

    @Test
    @Transactional
    void grade4_unexpected_suspected_returns_planned_action() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, true, true);
        var result = evaluator.apply(Map.of("aeId", aeId.toString()));
        var action = ((WorkerOutcome.Success) result.outcome()).plannedAction();
        assertThat(action).isNotNull();
        assertThat(action.actionType())
                .isEqualTo(ClinicalActionType.SUSAR_CRITERIA_DECISION.actionType());
        assertThat(result.output()).containsEntry("susarRequired", true);
        assertThat(result.output()).containsEntry("susarAssessmentComplete", true);
    }

    @Test
    @Transactional
    void grade5_unexpected_suspected_returns_planned_action() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_5, true, true);
        var result = evaluator.apply(Map.of("aeId", aeId.toString()));
        assertThat(((WorkerOutcome.Success) result.outcome()).plannedAction()).isNotNull();
        assertThat(result.output()).containsEntry("susarRequired", true);
    }

    @Test
    @Transactional
    void grade4_not_unexpected_returns_no_gate() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, false, true);
        var result = evaluator.apply(Map.of("aeId", aeId.toString()));
        assertThat(((WorkerOutcome.Success) result.outcome()).plannedAction()).isNull();
        assertThat(result.output()).containsEntry("susarRequired", false);
    }

    @Test
    @Transactional
    void grade3_unexpected_returns_no_gate() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_3, true, true);
        var result = evaluator.apply(Map.of("aeId", aeId.toString()));
        assertThat(((WorkerOutcome.Success) result.outcome()).plannedAction()).isNull();
    }

    @Test
    @Transactional
    void grade4_suspected_false_returns_no_gate() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, true, false);
        var result = evaluator.apply(Map.of("aeId", aeId.toString()));
        assertThat(((WorkerOutcome.Success) result.outcome()).plannedAction()).isNull();
    }

    @Test
    void missing_aeId_returns_no_gate() {
        var result = evaluator.apply(Map.of());
        assertThat(((WorkerOutcome.Success) result.outcome()).plannedAction()).isNull();
    }

    @Test
    void valid_uuid_not_in_db_returns_no_gate() {
        var result = evaluator.apply(Map.of("aeId", UUID.randomUUID().toString()));
        assertThat(((WorkerOutcome.Success) result.outcome()).plannedAction()).isNull();
    }

    private UUID persistAe(CtcaeGrade grade, boolean unexpected, boolean suspected) {
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
        ae.tenantId = "test-tenant";
        ae.persist();
        return aeId;
    }
}
