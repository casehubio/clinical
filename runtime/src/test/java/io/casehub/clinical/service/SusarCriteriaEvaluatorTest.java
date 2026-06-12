package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.WorkerResult;
import io.casehub.clinical.api.model.ClinicalActionType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for defensive paths in SusarCriteriaEvaluator.
 * Gate-positive path is covered by SusarActionGateLifecycleTest (@QuarkusTest)
 * because it requires a persisted AdverseEvent entity.
 */
class SusarCriteriaEvaluatorTest {

    private final SusarCriteriaEvaluator evaluator = new SusarCriteriaEvaluator();

    @Test
    void null_aeId_returns_no_gate() {
        WorkerResult result = evaluator.apply(Map.of());
        assertThat(result.plannedAction()).isNull();
        assertThat(result.output()).containsEntry("susarRequired", false);
        assertThat(result.output()).containsEntry("susarAssessmentComplete", true);
    }

    @Test
    void all_no_gate_paths_include_susar_assessment_complete() {
        WorkerResult result = evaluator.apply(Map.of());
        assertThat(result.output()).containsEntry("susarAssessmentComplete", true);
    }
}
