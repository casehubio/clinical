package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
class RbacBoundaryTest {

    @Inject FixedCurrentPrincipal principal;

    private String trialId;
    private String siteId;
    private String enrollmentId;

    @BeforeEach
    @Transactional
    void setup() {
        principal.reset();

        // Create trial directly (bypassing REST security)
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "RBAC-001";
        trial.phase = TrialPhase.PHASE_I;
        trial.sponsor = "RBAC-Sponsor";
        trial.targetEnrollment = 10;
        trial.status = TrialStatus.PLANNING;
        trial.tenantId = principal.tenancyId();
        trial.persist();
        trialId = trial.id.toString();

        // Create site directly
        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trial.id;
        site.investigatorId = "PI-001";
        site.tenantId = principal.tenancyId();
        site.persist();
        siteId = site.id.toString();

        // Create enrollment directly
        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.siteId = site.id;
        enrollment.patientId = "RBAC-PAT-001";
        enrollment.tenantId = principal.tenancyId();
        enrollment.persist();
        enrollmentId = enrollment.id.toString();
    }

    // --- MONITOR: zero write access (POST and PATCH) ---

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_post_trials() {
        given().contentType("application/json")
            .body("{\"protocolId\":\"X\",\"phase\":\"PHASE_I\",\"sponsor\":\"X\",\"targetEnrollment\":1}")
            .when().post("/trials")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_activate_trial() {
        given().when().post("/trials/" + trialId + "/activate")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_patch_sponsor_config() {
        given().contentType("application/json")
            .body("{\"connectorId\":\"x\",\"destination\":\"y\"}")
            .when().patch("/trials/" + trialId + "/sponsor-config")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_add_site() {
        given().contentType("application/json")
            .body("{\"investigatorId\":\"PI-X\"}")
            .when().post("/trials/" + trialId + "/sites")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_enroll_patient() {
        given().contentType("application/json")
            .body("{\"patientId\":\"PAT-X\"}")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/patients")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_screen_patient() {
        given().contentType("application/json")
            .body("{\"criteria\":[{\"criterionId\":\"C1\",\"met\":true}]}")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/screen")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_report_adverse_event() {
        given().contentType("application/json")
            .body("{\"grade\":\"GRADE_1\",\"occurredAt\":\"2026-01-01T00:00:00Z\"}")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/adverse-events")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_report_deviation() {
        given().contentType("application/json")
            .body("{\"deviationType\":\"dosing\",\"severity\":\"MINOR\"}")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/deviations")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_propose_amendment() {
        given().contentType("application/json")
            .body("{\"proposedChange\":\"change dosing\"}")
            .when().post("/trials/" + trialId + "/amendments")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_cannot_withdraw_consent() {
        given().contentType("application/json")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/withdraw-consent")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "monitor-user", roles = {ClinicalGroups.MONITOR})
    void monitor_can_read_trial() {
        given().when().get("/trials/" + trialId)
            .then().statusCode(200);
    }

    // --- COORDINATOR: excluded from governance and trial management ---

    @Test
    @TestSecurity(user = "coord-user", roles = {ClinicalGroups.COORDINATOR})
    void coordinator_cannot_create_trial() {
        given().contentType("application/json")
            .body("{\"protocolId\":\"X\",\"phase\":\"PHASE_I\",\"sponsor\":\"X\",\"targetEnrollment\":1}")
            .when().post("/trials")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "coord-user", roles = {ClinicalGroups.COORDINATOR})
    void coordinator_cannot_activate_trial() {
        given().when().post("/trials/" + trialId + "/activate")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "coord-user", roles = {ClinicalGroups.COORDINATOR})
    void coordinator_cannot_patch_sponsor_config() {
        given().contentType("application/json")
            .body("{\"connectorId\":\"x\",\"destination\":\"y\"}")
            .when().patch("/trials/" + trialId + "/sponsor-config")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "coord-user", roles = {ClinicalGroups.COORDINATOR})
    void coordinator_cannot_add_site() {
        given().contentType("application/json")
            .body("{\"investigatorId\":\"PI-X\"}")
            .when().post("/trials/" + trialId + "/sites")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "coord-user", roles = {ClinicalGroups.COORDINATOR})
    void coordinator_cannot_propose_amendment() {
        given().contentType("application/json")
            .body("{\"proposedChange\":\"change dosing\"}")
            .when().post("/trials/" + trialId + "/amendments")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "coord-user", roles = {ClinicalGroups.COORDINATOR})
    void coordinator_cannot_withdraw_consent() {
        given().contentType("application/json")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/withdraw-consent")
            .then().statusCode(403);
    }

    // --- INVESTIGATOR: excluded from sponsor-only trial management ---

    @Test
    @TestSecurity(user = "pi-user", roles = {ClinicalGroups.INVESTIGATOR})
    void investigator_cannot_create_trial() {
        given().contentType("application/json")
            .body("{\"protocolId\":\"X\",\"phase\":\"PHASE_I\",\"sponsor\":\"X\",\"targetEnrollment\":1}")
            .when().post("/trials")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "pi-user", roles = {ClinicalGroups.INVESTIGATOR})
    void investigator_cannot_activate_trial() {
        given().when().post("/trials/" + trialId + "/activate")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "pi-user", roles = {ClinicalGroups.INVESTIGATOR})
    void investigator_cannot_patch_sponsor_config() {
        given().contentType("application/json")
            .body("{\"connectorId\":\"x\",\"destination\":\"y\"}")
            .when().patch("/trials/" + trialId + "/sponsor-config")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "pi-user", roles = {ClinicalGroups.INVESTIGATOR})
    void investigator_cannot_add_site() {
        given().contentType("application/json")
            .body("{\"investigatorId\":\"PI-X\"}")
            .when().post("/trials/" + trialId + "/sites")
            .then().statusCode(403);
    }

    // --- SPONSOR: excluded from site-level clinical data entry ---

    @Test
    @TestSecurity(user = "sponsor-user", roles = {ClinicalGroups.SPONSOR})
    void sponsor_cannot_enroll_patient() {
        given().contentType("application/json")
            .body("{\"patientId\":\"PAT-X\"}")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/patients")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "sponsor-user", roles = {ClinicalGroups.SPONSOR})
    void sponsor_cannot_screen_patient() {
        given().contentType("application/json")
            .body("{\"criteria\":[{\"criterionId\":\"C1\",\"met\":true}]}")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/screen")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "sponsor-user", roles = {ClinicalGroups.SPONSOR})
    void sponsor_cannot_report_adverse_event() {
        given().contentType("application/json")
            .body("{\"grade\":\"GRADE_1\",\"occurredAt\":\"2026-01-01T00:00:00Z\"}")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/adverse-events")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "sponsor-user", roles = {ClinicalGroups.SPONSOR})
    void sponsor_cannot_report_deviation() {
        given().contentType("application/json")
            .body("{\"deviationType\":\"dosing\",\"severity\":\"MINOR\"}")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/deviations")
            .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "sponsor-user", roles = {ClinicalGroups.SPONSOR})
    void sponsor_cannot_withdraw_consent() {
        given().contentType("application/json")
            .when().post("/trials/" + trialId + "/sites/" + siteId + "/patients/" + enrollmentId + "/withdraw-consent")
            .then().statusCode(403);
    }

    // --- UNAUTHENTICATED ---

    @Test
    @TestSecurity  // no user, no roles — unauthenticated
    void unauthenticated_gets_401() {
        given().when().get("/trials/" + trialId)
            .then().statusCode(401);
    }
}
