package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.WorkerResult;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the defensive no-DB paths in SusarCriteriaEvaluator.
 *
 * <p>Note: {@code @Transactional} on {@code apply()} is a CDI interceptor — it has no
 * effect when the class is instantiated directly here (bypasses CDI). These tests
 * exercise only the early-return paths that do NOT call {@code AdverseEvent.findById()}.
 * Gate-positive and DB-loading paths are covered by SusarActionGateLifecycleTest.
 */
class SusarCriteriaEvaluatorTest {

    private final SusarCriteriaEvaluator evaluator = new SusarCriteriaEvaluator();

    @Test
    void null_aeId_returns_no_gate_with_assessment_complete() {
        WorkerResult result = evaluator.apply(Map.of());
        assertThat(result.plannedAction()).isNull();
        assertThat(result.output()).containsEntry("susarRequired", false);
        assertThat(result.output()).containsEntry("susarAssessmentComplete", true);
    }

    @Test
    void malformed_aeId_returns_no_gate() {
        WorkerResult result = evaluator.apply(Map.of("aeId", "not-a-uuid"));
        assertThat(result.plannedAction()).isNull();
        assertThat(result.output()).containsEntry("susarRequired", false);
        assertThat(result.output()).containsEntry("susarAssessmentComplete", true);
    }
}
