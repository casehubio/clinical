package io.casehub.clinical.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ClinicalActionTypeTest {

    @Test
    void susar_criteria_decision_has_correct_metadata() {
        ClinicalActionType type = ClinicalActionType.SUSAR_CRITERIA_DECISION;
        assertThat(type.actionType()).isEqualTo("susar.criteria.decision");
        assertThat(type.candidateGroups()).containsExactly("qualified-investigator");
        assertThat(type.reversible()).isFalse();
        assertThat(type.scope()).isEqualTo("casehubio/clinical/oversight");
        assertThat(type.expiresIn()).isNull();
        assertThat(type.reason()).contains("SUSAR");
    }

    @Test
    void susar_regulatory_filing_has_correct_metadata() {
        assertThat(ClinicalActionType.SUSAR_REGULATORY_FILING.actionType()).isEqualTo("susar.regulatory.filing");
        assertThat(ClinicalActionType.SUSAR_REGULATORY_FILING.candidateGroups()).containsExactly("qualified-investigator");
        assertThat(ClinicalActionType.SUSAR_REGULATORY_FILING.reversible()).isFalse();
    }

    @Test
    void patient_withdrawal_has_correct_metadata() {
        assertThat(ClinicalActionType.PATIENT_WITHDRAWAL.actionType()).isEqualTo("patient.withdrawal");
        assertThat(ClinicalActionType.PATIENT_WITHDRAWAL.candidateGroups()).containsExactly("principal-investigator");
        assertThat(ClinicalActionType.PATIENT_WITHDRAWAL.reversible()).isFalse();
    }

    @Test
    void dose_modification_is_reversible() {
        assertThat(ClinicalActionType.DOSE_MODIFICATION.actionType()).isEqualTo("dose.modification");
        assertThat(ClinicalActionType.DOSE_MODIFICATION.candidateGroups()).containsExactly("principal-investigator");
        assertThat(ClinicalActionType.DOSE_MODIFICATION.reversible()).isTrue();
    }

    @Test
    void protocol_deviation_recording_has_two_candidate_groups() {
        ClinicalActionType type = ClinicalActionType.PROTOCOL_DEVIATION_RECORDING;
        assertThat(type.actionType()).isEqualTo("protocol.deviation.recording");
        assertThat(type.candidateGroups()).containsExactly("principal-investigator", "irb-committee");
        assertThat(type.reversible()).isFalse();
    }

    @Test
    void fromActionType_round_trips_all_constants() {
        for (ClinicalActionType type : ClinicalActionType.values()) {
            assertThat(ClinicalActionType.fromActionType(type.actionType())).contains(type);
        }
    }

    @Test
    void fromActionType_returns_empty_for_unknown() {
        assertThat(ClinicalActionType.fromActionType("unknown.action")).isEmpty();
    }

    @Test
    void fromActionType_returns_empty_for_null() {
        assertThat(ClinicalActionType.fromActionType(null)).isEmpty();
    }

    @Test
    void all_types_share_oversight_scope() {
        for (ClinicalActionType type : ClinicalActionType.values()) {
            assertThat(type.scope()).isEqualTo("casehubio/clinical/oversight");
        }
    }

    @Test
    void susar_criteria_has_narrower_groups_than_deviation_recording() {
        // Fewer candidateGroups = more restrictive in ChainedReactiveActionRiskClassifier.narrower()
        // per GE-20260607-326c7e
        assertThat(ClinicalActionType.SUSAR_CRITERIA_DECISION.candidateGroups()).hasSize(1);
        assertThat(ClinicalActionType.PROTOCOL_DEVIATION_RECORDING.candidateGroups()).hasSize(2);
    }
}
