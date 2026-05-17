package io.casehub.clinical.service;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviationExpirationJobTest {

    @Inject DeviationExpirationJob job;

    private UUID siteId;

    @BeforeAll
    @Transactional
    void setup() {
        UUID trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId; trial.protocolId = "EXP"; trial.phase = TrialPhase.PHASE_I;
        trial.sponsor = "S"; trial.targetEnrollment = 5; trial.status = TrialStatus.ACTIVE;
        trial.persist();
        TrialSite site = new TrialSite();
        site.id = siteId; site.trialId = trialId; site.investigatorId = "pi-exp";
        site.persist();
    }

    @Test
    @Transactional
    void overdueCommandedDeviationIsMarkedExpired() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = siteId;
        dev.deviationType = "overdue"; dev.severity = DeviationSeverity.MINOR;
        dev.piApprovalStatus = PiApprovalStatus.COMMANDED;
        dev.escalationRequirement = EscalationRequirement.NONE;
        dev.piCommandChannelName = "clinical/deviation/" + dev.id + "/pi-oversight";
        dev.commandedAt = Instant.now().minus(10, ChronoUnit.DAYS);
        dev.responseDeadline = Instant.now().minus(3, ChronoUnit.DAYS);
        dev.persist();

        job.checkExpiredCommitments();

        ProtocolDeviation loaded = ProtocolDeviation.findById(dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.EXPIRED);
    }

    @Test
    @Transactional
    void futureDeadlineDeviationIsNotExpired() {
        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = siteId;
        dev.deviationType = "active"; dev.severity = DeviationSeverity.MINOR;
        dev.piApprovalStatus = PiApprovalStatus.COMMANDED;
        dev.escalationRequirement = EscalationRequirement.NONE;
        dev.piCommandChannelName = "clinical/deviation/" + dev.id + "/pi-oversight";
        dev.commandedAt = Instant.now();
        dev.responseDeadline = Instant.now().plus(7, ChronoUnit.DAYS);
        dev.persist();

        job.checkExpiredCommitments();

        ProtocolDeviation loaded = ProtocolDeviation.findById(dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.COMMANDED);
    }
}
