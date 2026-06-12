package io.casehub.clinical.service;

import io.casehub.api.model.WorkerResult;
import io.casehub.api.spi.PlannedAction;
import io.casehub.clinical.api.model.ClinicalActionType;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Default SUSAR criteria evaluator (7-day expedited path: Grade 4/5 only).
 *
 * <p>Gates when: {@code grade ∈ {GRADE_4, GRADE_5}} AND {@code unexpected == true}
 * AND {@code suspected != false} (absent key → conservative default per ICH E2A §I.A.1).
 *
 * <p>Grade 3 unexpected AEs (15-day path, 21 CFR 312.32(c)(1)(ii)) are NOT gated
 * here — deferred scope, tracked clinical#76.
 *
 * <p>Displacement: annotate a replacement with {@code @ApplicationScoped} (without
 * {@code @DefaultBean}) and implement {@link SusarEvaluatorFunction}.
 */
@DefaultBean
@ApplicationScoped
public class SusarCriteriaEvaluator implements SusarEvaluatorFunction {

    private static final Set<String> GATE_GRADES = Set.of(
            CtcaeGrade.GRADE_4.name(), CtcaeGrade.GRADE_5.name());

    /**
     * Evaluates SUSAR criteria for the AE identified by {@code context["aeId"]}.
     * Loads the entity directly from the DB — the engine's worker inputData
     * may not reliably propagate all initial context fields across JTA/async
     * boundaries, so the source of truth is the persisted entity.
     */
    @Override
    @Transactional
    public WorkerResult apply(final Map<String, Object> context) {
        final String aeIdStr = (String) context.get("aeId");
        if (aeIdStr == null) {
            return WorkerResult.of(Map.of("susarRequired", false, "susarAssessmentComplete", true));
        }
        final AdverseEvent ae = AdverseEvent.findById(UUID.fromString(aeIdStr));
        if (ae == null) {
            return WorkerResult.of(Map.of("susarRequired", false, "susarAssessmentComplete", true));
        }
        final boolean meetsGradeThreshold = GATE_GRADES.contains(ae.grade.name());
        if (meetsGradeThreshold && ae.unexpected && ae.suspected) {
            final Map<String, Object> actionCtx = new java.util.HashMap<>();
            actionCtx.put("aeId", aeIdStr);
            actionCtx.put("grade", ae.grade.name());
            actionCtx.put("unexpected", true);
            return WorkerResult.of(
                    Map.of("susarRequired", true, "susarAssessmentComplete", true),
                    PlannedAction.of(
                            ClinicalActionType.SUSAR_CRITERIA_DECISION.reason(),
                            ClinicalActionType.SUSAR_CRITERIA_DECISION.actionType(),
                            actionCtx));
        }
        return WorkerResult.of(Map.of("susarRequired", false, "susarAssessmentComplete", true));
    }
}
