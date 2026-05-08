package io.casehub.clinical.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end happy-path test for the 3-site oncology showcase scenario.
 * Verifies the domain layer can support the full trial registration flow
 * that Epic 3 will wire to sub-case orchestration.
 */
@QuarkusTest
class ShowcaseScenarioTest {

    @Test
    void three_site_oncology_trial_registers_correctly() {
        // Register the trial — UUID suffix avoids H2 uniqueness collision across test runs
        String trialLoc = given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId": "ONCOL-PHASE3-2026-001-%s",
                  "phase": "PHASE_III",
                  "sponsor": "Acme Oncology",
                  "targetEnrollment": 300
                }
                """.formatted(UUID.randomUUID()))
        .when().post("/trials").then().statusCode(201).extract().header("Location");

        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        // Add 3 sites
        UUID siteAId = addSite(trialId, "pi-site-a-001");
        UUID siteBId = addSite(trialId, "pi-site-b-002");
        UUID siteCId = addSite(trialId, "pi-site-c-003");

        // Enroll a patient at each site
        UUID patientA = enrollPatient(trialId, siteAId, "PATIENT-SITE-A-001");
        UUID patientB = enrollPatient(trialId, siteBId, "PATIENT-SITE-B-001");
        UUID patientC = enrollPatient(trialId, siteCId, "PATIENT-SITE-C-001");

        // Verify trial
        given().when().get("/trials/{id}", trialId)
        .then()
            .statusCode(200)
            .body("phase", equalTo("PHASE_III"))
            .body("targetEnrollment", equalTo(300))
            .body("status", equalTo("PLANNING"));

        // Verify all 3 sites are retrievable under the trial
        assertSiteExists(trialId, siteAId, "pi-site-a-001");
        assertSiteExists(trialId, siteBId, "pi-site-b-002");
        assertSiteExists(trialId, siteCId, "pi-site-c-003");

        // Verify all 3 patients enrolled as CANDIDATE/PENDING
        assertEnrollmentExists(trialId, siteAId, patientA, "PATIENT-SITE-A-001");
        assertEnrollmentExists(trialId, siteBId, patientB, "PATIENT-SITE-B-001");
        assertEnrollmentExists(trialId, siteCId, patientC, "PATIENT-SITE-C-001");
    }

    private UUID addSite(UUID trialId, String investigatorId) {
        String loc = given()
            .contentType("application/json")
            .body("{\"investigatorId\": \"" + investigatorId + "\"}")
        .when()
            .post("/trials/{id}/sites", trialId)
        .then()
            .statusCode(201).extract().header("Location");
        return UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));
    }

    private UUID enrollPatient(UUID trialId, UUID siteId, String patientId) {
        String loc = given()
            .contentType("application/json")
            .body("{\"patientId\": \"" + patientId + "\"}")
        .when()
            .post("/trials/{trialId}/sites/{siteId}/patients", trialId, siteId)
        .then()
            .statusCode(201).extract().header("Location");
        return UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));
    }

    private void assertSiteExists(UUID trialId, UUID siteId, String investigatorId) {
        given().when().get("/trials/{trialId}/sites/{siteId}", trialId, siteId)
        .then()
            .statusCode(200)
            .body("investigatorId", equalTo(investigatorId))
            .body("status", equalTo("PENDING"));
    }

    private void assertEnrollmentExists(UUID trialId, UUID siteId, UUID enrollmentId, String patientId) {
        given().when().get("/trials/{t}/sites/{s}/patients/{e}", trialId, siteId, enrollmentId)
        .then()
            .statusCode(200)
            .body("patientId", equalTo(patientId))
            .body("enrollmentStatus", equalTo("CANDIDATE"))
            .body("consentStatus", equalTo("PENDING"));
    }
}
