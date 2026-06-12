package io.casehub.clinical.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.spi.PlannedAction;
import io.casehub.api.spi.RiskDecision;
import io.casehub.clinical.api.model.ClinicalActionType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClinicalActionRiskClassifierTest {

    private final ClinicalActionRiskClassifier classifier = new ClinicalActionRiskClassifier();

    @Test
    void susar_criteria_decision_returns_gate_required_with_correct_fields() {
        PlannedAction action = PlannedAction.of(
                "SUSAR test", ClinicalActionType.SUSAR_CRITERIA_DECISION.actionType(), Map.of());
        RiskDecision decision = classifier.classify(action);
        assertThat(decision).isInstanceOf(RiskDecision.GateRequired.class);
        RiskDecision.GateRequired gate = (RiskDecision.GateRequired) decision;
        assertThat(gate.candidateGroups()).containsExactly("qualified-investigator");
        assertThat(gate.reversible()).isFalse();
        assertThat(gate.scope()).isEqualTo("casehubio/clinical/oversight");
        assertThat(gate.expiresIn()).isNull();
        assertThat(gate.reason()).isEqualTo(ClinicalActionType.SUSAR_CRITERIA_DECISION.reason());
    }

    @Test
    void susar_regulatory_filing_returns_gate_required() {
        PlannedAction action = PlannedAction.of(
                "filing", ClinicalActionType.SUSAR_REGULATORY_FILING.actionType(), Map.of());
        assertThat(classifier.classify(action)).isInstanceOf(RiskDecision.GateRequired.class);
    }

    @Test
    void patient_withdrawal_returns_gate_required_with_pi_group() {
        PlannedAction action = PlannedAction.of(
                "withdrawal", ClinicalActionType.PATIENT_WITHDRAWAL.actionType(), Map.of());
        RiskDecision.GateRequired gate = (RiskDecision.GateRequired) classifier.classify(action);
        assertThat(gate.candidateGroups()).containsExactly("principal-investigator");
        assertThat(gate.reversible()).isFalse();
    }

    @Test
    void dose_modification_returns_reversible_gate_required() {
        PlannedAction action = PlannedAction.of(
                "dose", ClinicalActionType.DOSE_MODIFICATION.actionType(), Map.of());
        RiskDecision.GateRequired gate = (RiskDecision.GateRequired) classifier.classify(action);
        assertThat(gate.reversible()).isTrue();
    }

    @Test
    void protocol_deviation_recording_returns_gate_with_two_groups() {
        PlannedAction action = PlannedAction.of(
                "dev", ClinicalActionType.PROTOCOL_DEVIATION_RECORDING.actionType(), Map.of());
        RiskDecision.GateRequired gate = (RiskDecision.GateRequired) classifier.classify(action);
        assertThat(gate.candidateGroups()).containsExactly("principal-investigator", "irb-committee");
    }

    @Test
    void unknown_action_type_returns_autonomous() {
        PlannedAction action = PlannedAction.of("test", "some.unknown.action", Map.of());
        assertThat(classifier.classify(action)).isInstanceOf(RiskDecision.Autonomous.class);
    }

    @Test
    void null_planned_action_returns_autonomous() {
        assertThat(classifier.classify(null)).isInstanceOf(RiskDecision.Autonomous.class);
    }

    @Test
    void null_action_type_returns_autonomous() {
        PlannedAction action = PlannedAction.of("test", null, Map.of());
        assertThat(classifier.classify(action)).isInstanceOf(RiskDecision.Autonomous.class);
    }
}
