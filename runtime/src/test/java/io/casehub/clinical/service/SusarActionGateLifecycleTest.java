package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.WorkerResult;
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
 * Layer 8 integration test — SusarCriteriaEvaluator @Transactional entity loading.
 *
 * <p>Verifies that SusarCriteriaEvaluator correctly loads an AdverseEvent from the DB
 * using the aeId from context and evaluates SUSAR criteria against entity fields.
 *
 * <p>Note: the full engine gate lifecycle (gate WorkItem creation on PlannedAction)
 * is blocked by an engine timing issue and tracked in clinical#77.
 */
@QuarkusTest
class SusarActionGateLifecycleTest {

    @Inject SusarCriteriaEvaluator evaluator;

    @Test
    @Transactional
    void grade4_unexpected_suspected_returns_planned_action() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, true, true);

        WorkerResult result = evaluator.apply(Map.of("aeId", aeId.toString()));

        assertThat(result.plannedAction()).isNotNull();
        assertThat(result.plannedAction().actionType())
                .isEqualTo(ClinicalActionType.SUSAR_CRITERIA_DECISION.actionType());
        assertThat(result.plannedAction().description())
                .isEqualTo(ClinicalActionType.SUSAR_CRITERIA_DECISION.reason());
        assertThat(result.output()).containsEntry("susarRequired", true);
        assertThat(result.output()).containsEntry("susarAssessmentComplete", true);
    }

    @Test
    @Transactional
    void grade5_unexpected_suspected_returns_planned_action() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_5, true, true);

        WorkerResult result = evaluator.apply(Map.of("aeId", aeId.toString()));

        assertThat(result.plannedAction()).isNotNull();
        assertThat(result.output()).containsEntry("susarRequired", true);
    }

    @Test
    @Transactional
    void grade4_not_unexpected_returns_no_gate() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, false, true);

        WorkerResult result = evaluator.apply(Map.of("aeId", aeId.toString()));

        assertThat(result.plannedAction()).isNull();
        assertThat(result.output()).containsEntry("susarRequired", false);
        assertThat(result.output()).containsEntry("susarAssessmentComplete", true);
    }

    @Test
    @Transactional
    void grade3_unexpected_returns_no_gate() {
        // Grade 3 is 15-day expedited path — deferred (clinical#76)
        UUID aeId = persistAe(CtcaeGrade.GRADE_3, true, true);

        WorkerResult result = evaluator.apply(Map.of("aeId", aeId.toString()));

        assertThat(result.plannedAction()).isNull();
        assertThat(result.output()).containsEntry("susarRequired", false);
    }

    @Test
    @Transactional
    void grade4_unexpected_suspected_false_returns_no_gate() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, true, false);

        WorkerResult result = evaluator.apply(Map.of("aeId", aeId.toString()));

        assertThat(result.plannedAction()).isNull();
        assertThat(result.output()).containsEntry("susarRequired", false);
    }

    @Test
    void missing_ae_id_returns_no_gate() {
        WorkerResult result = evaluator.apply(Map.of());
        assertThat(result.plannedAction()).isNull();
        assertThat(result.output()).containsEntry("susarRequired", false);
        assertThat(result.output()).containsEntry("susarAssessmentComplete", true);
    }

    @Test
    void valid_uuid_not_in_db_returns_no_gate() {
        // Simulates the engine timing issue: worker fires before entity is committed
        WorkerResult result = evaluator.apply(Map.of("aeId", UUID.randomUUID().toString()));
        assertThat(result.plannedAction()).isNull();
        assertThat(result.output()).containsEntry("susarRequired", false);
        assertThat(result.output()).containsEntry("susarAssessmentComplete", true);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID persistAe(final CtcaeGrade grade, final boolean unexpected, final boolean suspected) {
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
