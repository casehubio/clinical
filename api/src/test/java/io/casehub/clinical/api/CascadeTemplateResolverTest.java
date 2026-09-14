package io.casehub.clinical.api;

import io.casehub.clinical.api.model.CtcaeGrade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CascadeTemplateResolverTest {

    @Test
    void grade1ReturnsShortTemplate() {
        List<CascadeStepType> steps = CascadeTemplateResolver.resolve(CtcaeGrade.GRADE_1, false, false);
        assertThat(steps).containsExactly(
            CascadeStepType.AE_REPORTED,
            CascadeStepType.SLA_ASSIGNED,
            CascadeStepType.LEDGER_SEALED);
    }

    @Test
    void grade2ReturnsShortTemplate() {
        List<CascadeStepType> steps = CascadeTemplateResolver.resolve(CtcaeGrade.GRADE_2, false, false);
        assertThat(steps).containsExactly(
            CascadeStepType.AE_REPORTED,
            CascadeStepType.SLA_ASSIGNED,
            CascadeStepType.LEDGER_SEALED);
    }

    @Test
    void grade3BaselineReturnsEscalationTemplate() {
        List<CascadeStepType> steps = CascadeTemplateResolver.resolve(CtcaeGrade.GRADE_3, false, false);
        assertThat(steps).containsExactly(
            CascadeStepType.AE_REPORTED,
            CascadeStepType.SLA_ASSIGNED,
            CascadeStepType.ESCALATION_CASE_STARTED,
            CascadeStepType.AGENT_SELECTED,
            CascadeStepType.AGENT_REASONING,
            CascadeStepType.AGENT_RESULT,
            CascadeStepType.SAFETY_OFFICER_NOTIFIED,
            CascadeStepType.LEDGER_SEALED);
    }

    @Test
    void grade4UnexpectedAddsRegulatorySubmission() {
        List<CascadeStepType> steps = CascadeTemplateResolver.resolve(CtcaeGrade.GRADE_4, true, false);
        assertThat(steps).contains(CascadeStepType.REGULATORY_SUBMISSION_STARTED);
        assertThat(steps).doesNotContain(CascadeStepType.GATE_OPENED, CascadeStepType.GATE_RESOLVED);
    }

    @Test
    void grade4UnexpectedSuspectedAddsGate() {
        List<CascadeStepType> steps = CascadeTemplateResolver.resolve(CtcaeGrade.GRADE_4, true, true);
        assertThat(steps).contains(
            CascadeStepType.GATE_OPENED,
            CascadeStepType.GATE_RESOLVED,
            CascadeStepType.REGULATORY_SUBMISSION_STARTED);
    }

    @Test
    void grade5FullSusarTemplate() {
        List<CascadeStepType> steps = CascadeTemplateResolver.resolve(CtcaeGrade.GRADE_5, true, true);
        assertThat(steps).containsExactly(
            CascadeStepType.AE_REPORTED,
            CascadeStepType.SLA_ASSIGNED,
            CascadeStepType.ESCALATION_CASE_STARTED,
            CascadeStepType.AGENT_SELECTED,
            CascadeStepType.AGENT_REASONING,
            CascadeStepType.AGENT_RESULT,
            CascadeStepType.GATE_OPENED,
            CascadeStepType.GATE_RESOLVED,
            CascadeStepType.SAFETY_OFFICER_NOTIFIED,
            CascadeStepType.REGULATORY_SUBMISSION_STARTED,
            CascadeStepType.LEDGER_SEALED);
    }

    @Test
    void grade2NeverHasEscalation() {
        List<CascadeStepType> steps = CascadeTemplateResolver.resolve(CtcaeGrade.GRADE_2, true, true);
        assertThat(steps).doesNotContain(CascadeStepType.ESCALATION_CASE_STARTED);
    }

    @Test
    void templateIsImmutable() {
        List<CascadeStepType> steps = CascadeTemplateResolver.resolve(CtcaeGrade.GRADE_4, false, false);
        assertThat(steps).isUnmodifiable();
    }
}
