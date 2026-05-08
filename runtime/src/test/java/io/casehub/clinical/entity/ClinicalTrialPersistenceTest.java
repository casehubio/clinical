package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
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
}
