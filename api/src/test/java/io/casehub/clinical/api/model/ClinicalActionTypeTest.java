package io.casehub.clinical.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.spi.routing.StaticSetStrategy;
import org.junit.jupiter.api.Test;

class ClinicalActionTypeTest {

    private static java.util.Set<String> groups(ClinicalActionType type) {
        return ((StaticSetStrategy) type.candidateGroups()).values();
    }

    @Test
    void susar_criteria_decision_has_correct_metadata() {
        ClinicalActionType type = ClinicalActionType.SUSAR_CRITERIA_DECISION;
        assertThat(type.actionType()).isEqualTo("susar.criteria.decision");
        assertThat(groups(type)).containsExactly("qualified-investigator");
        assertThat(type.reversible()).isFalse();
        assertThat(type.scope()).isEqualTo("casehubio/clinical/oversight");
        assertThat(type.expiresIn()).isNull();
        assertThat(type.reason()).contains("SUSAR");
    }

    @Test
    void susar_regulatory_filing_has_correct_metadata() {
        assertThat(ClinicalActionType.SUSAR_REGULATORY_FILING.actionType()).isEqualTo("susar.regulatory.filing");
        assertThat(groups(ClinicalActionType.SUSAR_REGULATORY_FILING)).containsExactly("qualified-investigator");
        assertThat(ClinicalActionType.SUSAR_REGULATORY_FILING.reversible()).isFalse();
    }

    @Test
    void patient_withdrawal_has_correct_metadata() {
        assertThat(ClinicalActionType.PATIENT_WITHDRAWAL.actionType()).isEqualTo("patient.withdrawal");
        assertThat(groups(ClinicalActionType.PATIENT_WITHDRAWAL)).containsExactly("principal-investigator");
        assertThat(ClinicalActionType.PATIENT_WITHDRAWAL.reversible()).isFalse();
    }

    @Test
    void dose_modification_is_reversible() {
        assertThat(ClinicalActionType.DOSE_MODIFICATION.actionType()).isEqualTo("dose.modification");
        assertThat(groups(ClinicalActionType.DOSE_MODIFICATION)).containsExactly("principal-investigator");
        assertThat(ClinicalActionType.DOSE_MODIFICATION.reversible()).isTrue();
    }

    @Test
    void protocol_deviation_recording_has_two_candidate_groups() {
        ClinicalActionType type = ClinicalActionType.PROTOCOL_DEVIATION_RECORDING;
        assertThat(type.actionType()).isEqualTo("protocol.deviation.recording");
        assertThat(groups(type)).containsExactlyInAnyOrder("principal-investigator", "irb-committee");
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
        assertThat(groups(ClinicalActionType.SUSAR_CRITERIA_DECISION)).hasSize(1);
        assertThat(groups(ClinicalActionType.PROTOCOL_DEVIATION_RECORDING)).hasSize(2);
    }
}
