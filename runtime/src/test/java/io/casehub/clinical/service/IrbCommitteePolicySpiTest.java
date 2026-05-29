package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.spi.IrbCommitteeAssignment;
import io.casehub.clinical.api.spi.IrbCommitteeAssignmentPolicy;
import io.casehub.clinical.api.spi.IrbCommitteeContext;
import io.casehub.clinical.entity.IrbApproval;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that IrbDeviationCaseService delegates committee assignment to the
 * IrbCommitteeAssignmentPolicy SPI and that an @Alternative implementation overrides the default.
 */
@QuarkusTest
@TestProfile(IrbCommitteePolicySpiTest.TestIrbProfile.class)
class IrbCommitteePolicySpiTest {

    static final String TEST_COMMITTEE_ID = "test-irb-committee-xyz";

    @Alternative
    @ApplicationScoped
    static class TestIrbCommitteeAssignmentPolicy implements IrbCommitteeAssignmentPolicy {
        @Override
        public IrbCommitteeAssignment evaluate(IrbCommitteeContext context) {
            return new IrbCommitteeAssignment(TEST_COMMITTEE_ID, List.of(TEST_COMMITTEE_ID));
        }
    }

    public static class TestIrbProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(TestIrbCommitteeAssignmentPolicy.class);
        }
    }

    @Inject IrbDeviationCaseService irbDeviationCaseService;

    private UUID deviationId;
    private UUID siteId;
    private UUID trialId;

    @BeforeEach
    @Transactional
    void setup() {
        deviationId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        trialId = UUID.randomUUID();

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "test-pi";
        site.persist();

        ProtocolDeviation deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.siteId = siteId;
        deviation.deviationType = "CONSENT_DEVIATION";
        deviation.severity = DeviationSeverity.CRITICAL;
        deviation.piApprovalStatus = PiApprovalStatus.APPROVED;
        deviation.persist();
    }

    @Test
    void irb_approval_reflects_alternative_policy_committee_id() {
        // onDeviationResolved called directly (synchronous, not via CDI async bus)
        // — all three phases complete before this line returns
        irbDeviationCaseService.onDeviationResolved(criticalDeviationApproved());

        IrbApproval approval = findApproval(deviationId);
        assertThat(approval).isNotNull();
        assertThat(approval.committeeId).isEqualTo(TEST_COMMITTEE_ID);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Transactional
    IrbApproval findApproval(UUID forDeviationId) {
        return IrbApproval.find("deviationId = ?1", forDeviationId).firstResult();
    }

    private ProtocolDeviationResolvedEvent criticalDeviationApproved() {
        return new ProtocolDeviationResolvedEvent(
                deviationId, siteId, DeviationSeverity.CRITICAL,
                EscalationRequirement.IRB_REVIEW, PiApprovalStatus.APPROVED,
                "CONSENT_DEVIATION", "pi-001");
    }
}
