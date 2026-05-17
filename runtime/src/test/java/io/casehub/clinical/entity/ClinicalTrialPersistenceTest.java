package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class ClinicalTrialPersistenceTest {

    @Inject EntityManager em;

    @Test
    @Transactional
    void clinical_trial_round_trips() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "ONCOL-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Acme Pharma";
        trial.targetEnrollment = 150;
        trial.status = TrialStatus.PLANNING;
        em.persist(trial);
        em.flush();
        em.clear();

        ClinicalTrial found = em.find(ClinicalTrial.class, trial.id);
        assertThat(found.protocolId).isEqualTo("ONCOL-001");
        assertThat(found.phase).isEqualTo(TrialPhase.PHASE_III);
        assertThat(found.targetEnrollment).isEqualTo(150);
        assertThat(found.status).isEqualTo(TrialStatus.PLANNING);
    }

    @Test
    @Transactional
    void trial_site_links_to_trial() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "ONCOL-002";
        trial.phase = TrialPhase.PHASE_II;
        trial.sponsor = "BioTest";
        trial.targetEnrollment = 50;
        trial.status = TrialStatus.PLANNING;
        em.persist(trial);

        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trial.id;
        site.investigatorId = "pi-alice-001";
        site.status = SiteStatus.PENDING;
        em.persist(site);
        em.flush();
        em.clear();

        TrialSite found = em.find(TrialSite.class, site.id);
        assertThat(found.trialId).isEqualTo(trial.id);
        assertThat(found.investigatorId).isEqualTo("pi-alice-001");
    }

    @Test
    @Transactional
    void protocolDeviationPersistsWithCommandedFields() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "PROT-001";
        trial.phase = TrialPhase.PHASE_II;
        trial.sponsor = "Sponsor";
        trial.targetEnrollment = 10;
        trial.status = TrialStatus.ACTIVE;
        em.persist(trial);

        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trial.id;
        site.investigatorId = "pi-001";
        em.persist(site);

        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.siteId = site.id;
        dev.deviationType = "sample-window";
        dev.severity = DeviationSeverity.MAJOR;
        dev.piApprovalStatus = PiApprovalStatus.COMMANDED;
        dev.piCommandChannelName = "clinical/deviation/" + dev.id + "/pi-oversight";
        dev.commandedAt = Instant.now();
        dev.responseDeadline = Instant.now().plus(72, ChronoUnit.HOURS);
        dev.escalationRequirement = EscalationRequirement.SPONSOR_NOTIFICATION;
        em.persist(dev);
        em.flush();
        em.clear();

        ProtocolDeviation loaded = em.find(ProtocolDeviation.class, dev.id);
        assertThat(loaded.piApprovalStatus).isEqualTo(PiApprovalStatus.COMMANDED);
        assertThat(loaded.escalationRequirement).isEqualTo(EscalationRequirement.SPONSOR_NOTIFICATION);
        assertThat(loaded.piCommandChannelName).startsWith("clinical/deviation/");
    }
}
