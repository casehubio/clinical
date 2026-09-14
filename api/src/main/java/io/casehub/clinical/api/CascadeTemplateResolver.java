package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;

import java.util.ArrayList;
import java.util.List;

public final class CascadeTemplateResolver {

    private CascadeTemplateResolver() {}

    public static List<CascadeStepType> resolve(CtcaeGrade grade, boolean unexpected, boolean suspected) {
        List<CascadeStepType> steps = new ArrayList<>();
        steps.add(CascadeStepType.AE_REPORTED);
        steps.add(CascadeStepType.SLA_ASSIGNED);

        if (grade.ordinal() < CtcaeGrade.GRADE_3.ordinal()) {
            steps.add(CascadeStepType.LEDGER_SEALED);
            return List.copyOf(steps);
        }

        steps.add(CascadeStepType.ESCALATION_CASE_STARTED);
        steps.add(CascadeStepType.AGENT_SELECTED);
        steps.add(CascadeStepType.AGENT_REASONING);
        steps.add(CascadeStepType.AGENT_RESULT);

        if (unexpected && suspected) {
            steps.add(CascadeStepType.GATE_OPENED);
            steps.add(CascadeStepType.GATE_RESOLVED);
        }

        steps.add(CascadeStepType.SAFETY_OFFICER_NOTIFIED);

        if (unexpected) {
            steps.add(CascadeStepType.REGULATORY_SUBMISSION_STARTED);
        }

        steps.add(CascadeStepType.LEDGER_SEALED);
        return List.copyOf(steps);
    }
}
