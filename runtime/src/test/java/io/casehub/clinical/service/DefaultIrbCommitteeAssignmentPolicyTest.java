package io.casehub.clinical.service;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.spi.IrbCommitteeAssignment;
import io.casehub.clinical.api.spi.IrbCommitteeContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultIrbCommitteeAssignmentPolicyTest {

    private final DefaultIrbCommitteeAssignmentPolicy policy = new DefaultIrbCommitteeAssignmentPolicy();

    @Test
    void evaluate_returns_non_null_assignment_for_any_context() {
        IrbCommitteeContext ctx = new IrbCommitteeContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), DeviationSeverity.CRITICAL);

        IrbCommitteeAssignment result = policy.evaluate(ctx);

        assertThat(result).isNotNull();
        assertThat(result.committeeId()).isNotBlank();
        assertThat(result.candidateGroups()).isNotEmpty();
    }

    @Test
    void evaluate_returns_irb_committee_default_regardless_of_input() {
        IrbCommitteeContext ctx = new IrbCommitteeContext(
                UUID.randomUUID(), UUID.randomUUID(), null, DeviationSeverity.MINOR);

        IrbCommitteeAssignment result = policy.evaluate(ctx);

        assertThat(result.committeeId()).isEqualTo("irb-committee");
        assertThat(result.candidateGroups()).containsExactly("irb-committee");
    }
}
