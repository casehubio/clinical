package io.casehub.clinical.playbook;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.pages.playbook.client.ActionContext;
import io.casehub.pages.playbook.client.ActionRegistry;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class ClinicalPlaybookActionsTest {

    @Inject ClinicalPlaybookActions actions;
    @Inject FixedCurrentPrincipal principal;
    @Inject
            jakarta.persistence.EntityManager em;


    @AfterEach
    void resetPrincipal() { principal.reset(); }

    private static ActionContext ctx(Map<String, Object> data) {
        return ActionContext.of("test-actor", data, Map.of());
    }

    @Test
    void discovers_playbook_actions_on_the_cdi_proxy() {
        assertEquals(Set.of("createTrial", "activateTrial", "addSite", "enrollPatient",
                "reportAdverseEvent", "reportDeviation", "verifyLedger"),
                ActionRegistry.scan(List.of(actions)).actions());
    }

    @Test
    @Transactional
    void createTrial_returns_trialId() throws Exception {
        var result = ActionRegistry.scan(List.of(actions)).invoke("createTrial", ctx(Map.of(
                "protocolId", "PLAYBOOK-" + UUID.randomUUID(),
                "phase", "PHASE_III",
                "sponsor", "Test Sponsor",
                "targetEnrollment", 100)));

        assertNotNull(result.get("trialId"));
        ClinicalTrial trial = em.find(ClinicalTrial.class, UUID.fromString(result.get("trialId").toString()));
        assertNotNull(trial);
        assertEquals("PHASE_III", trial.phase.name());
        assertEquals("PLANNING", trial.status.name());
    }

    @Test
    @Transactional
    void addSite_returns_siteId() {
        String trialId = actions.createTrial(ctx(Map.of(
                "protocolId", "SITE-" + UUID.randomUUID(),
                "phase", "PHASE_II", "sponsor", "S", "targetEnrollment", 10)))
                .get("trialId").toString();

        var result = actions.addSite(ctx(Map.of("trialId", trialId, "investigatorId", "dr-test")));

        assertNotNull(result.get("siteId"));
        TrialSite site = em.find(TrialSite.class, UUID.fromString(result.get("siteId").toString()));
        assertNotNull(site);
        assertEquals("dr-test", site.investigatorId);
    }

    @Test
    @Transactional
    void enrollPatient_returns_enrollmentId() {
        String trialId = actions.createTrial(ctx(Map.of(
                "protocolId", "ENROLL-" + UUID.randomUUID(),
                "phase", "PHASE_II", "sponsor", "S", "targetEnrollment", 10)))
                .get("trialId").toString();
        String siteId = actions.addSite(ctx(Map.of("trialId", trialId, "investigatorId", "dr-test")))
                .get("siteId").toString();

        var result = actions.enrollPatient(ctx(Map.of(
                "trialId", trialId, "siteId", siteId, "patientId", "PAT-001")));

        assertNotNull(result.get("enrollmentId"));
        PatientEnrollment enrollment = em.find(PatientEnrollment.class,
                UUID.fromString(result.get("enrollmentId").toString()));
        assertNotNull(enrollment);
        assertEquals("PAT-001", enrollment.patientId);
    }
}
