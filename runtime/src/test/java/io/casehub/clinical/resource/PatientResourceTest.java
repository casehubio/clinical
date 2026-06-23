package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class PatientResourceTest {

    @Inject FixedCurrentPrincipal principal;

    @AfterEach
    void resetPrincipal() { principal.reset(); }

    private UUID[] createTrialAndSiteIds() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ISO-P-" + java.util.UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        String siteLoc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-iso\"}")
            .when().post("/trials/{id}/sites", trialId).then().statusCode(201).extract().header("Location");
        UUID siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));
        return new UUID[]{trialId, siteId};
    }

    /** Creates a trial and site, returns the siteId. */
    private UUID createTrialAndSite() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"PAT-TEST-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
        .when().post("/trials").then().statusCode(201).extract().header("Location");

        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        String siteLoc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-carol-003\"}")
        .when().post("/trials/{id}/sites", trialId).then().statusCode(201).extract().header("Location");

        return UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));
    }

    @Test
    void enroll_patient_returns_201_with_location() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ENROLL-001-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
        .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID tid = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));
        String siteLoc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-x\"}")
        .when().post("/trials/{id}/sites", tid).then().statusCode(201).extract().header("Location");
        UUID sid = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));

        given()
            .contentType("application/json")
            .body("{\"patientId\": \"PATIENT-ALPHA-001\"}")
        .when()
            .post("/trials/{trialId}/sites/{siteId}/patients", tid, sid)
        .then()
            .statusCode(201)
            .header("Location", containsString("/patients/"));
    }

    @Test
    void get_enrollment_returns_status_fields() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ENROLL-002-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
        .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID tid = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));
        String siteLoc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-y\"}")
        .when().post("/trials/{id}/sites", tid).then().statusCode(201).extract().header("Location");
        UUID sid = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));

        String patientLoc = given()
            .contentType("application/json")
            .body("{\"patientId\": \"PATIENT-BETA-002\"}")
        .when()
            .post("/trials/{trialId}/sites/{siteId}/patients", tid, sid)
        .then()
            .statusCode(201).extract().header("Location");

        given().when().get(patientLoc)
        .then()
            .statusCode(200)
            .body("patientId", equalTo("PATIENT-BETA-002"))
            .body("consentStatus", equalTo("PENDING"))
            .body("enrollmentStatus", equalTo("CANDIDATE"));
    }

    @Test
    void enroll_to_unknown_site_returns_404() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ENROLL-003-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
        .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID tid = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        given()
            .contentType("application/json")
            .body("{\"patientId\":\"PATIENT-X\"}")
        .when()
            .post("/trials/{trialId}/sites/{siteId}/patients", tid, UUID.randomUUID())
        .then()
            .statusCode(404);
    }

    @Test
    void get_enrollment_via_wrong_trial_returns_404() {
        // Create trial A with a site and patient
        String trialLocA = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"WRONG-TRIAL-A-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
        .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID tidA = UUID.fromString(trialLocA.substring(trialLocA.lastIndexOf('/') + 1));
        String siteLocA = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-a\"}")
        .when().post("/trials/{id}/sites", tidA).then().statusCode(201).extract().header("Location");
        UUID sidA = UUID.fromString(siteLocA.substring(siteLocA.lastIndexOf('/') + 1));
        String patientLoc = given()
            .contentType("application/json")
            .body("{\"patientId\":\"PAT-CROSS\"}")
        .when().post("/trials/{t}/sites/{s}/patients", tidA, sidA).then().statusCode(201).extract().header("Location");
        UUID enrollmentId = UUID.fromString(patientLoc.substring(patientLoc.lastIndexOf('/') + 1));

        // Create trial B — try to access the patient via trial B
        String trialLocB = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"WRONG-TRIAL-B-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
        .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID tidB = UUID.fromString(trialLocB.substring(trialLocB.lastIndexOf('/') + 1));

        given().when().get("/trials/{t}/sites/{s}/patients/{e}", tidB, sidA, enrollmentId)
        .then().statusCode(404);
    }

    @Test
    void get_enrollment_returns_404_for_wrong_tenant() {
        UUID[] ids = createTrialAndSiteIds();
        UUID trialId = ids[0], siteId = ids[1];

        String patientLoc = given()
            .contentType("application/json")
            .body("{\"patientId\":\"PAT-ISO-001\"}")
            .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId)
            .then().statusCode(201).extract().header("Location");

        principal.setTenancyId("other-tenant");
        given().when().get(patientLoc).then().statusCode(404);
    }

    @Test
    void report_ae_returns_404_for_wrong_tenant_enrollment() {
        UUID[] ids = createTrialAndSiteIds();
        UUID trialId = ids[0], siteId = ids[1];

        String patientLoc = given()
            .contentType("application/json")
            .body("{\"patientId\":\"PAT-ISO-002\"}")
            .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId)
            .then().statusCode(201).extract().header("Location");
        UUID enrollmentId = UUID.fromString(patientLoc.substring(patientLoc.lastIndexOf('/') + 1));

        principal.setTenancyId("other-tenant");
        given()
            .contentType("application/json")
            .body("{\"grade\":\"GRADE_1\",\"occurredAt\":\"2026-01-01T10:00:00Z\"}")
            .when().post("/trials/{t}/sites/{s}/patients/{e}/adverse-events",
                trialId, siteId, enrollmentId)
            .then().statusCode(404);
    }

    @Test
    void enrollment_inherits_site_tenantId_not_principal_tenantId() {
        UUID[] ids = createTrialAndSiteIds();
        UUID trialId = ids[0], siteId = ids[1];

        principal.setTenancyId("admin-tenant");
        principal.setCrossTenantAdmin(true);
        String patientLoc = given()
            .contentType("application/json")
            .body("{\"patientId\":\"PAT-ISO-INHERIT\"}")
            .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId)
            .then().statusCode(201).extract().header("Location");

        principal.reset();
        given().when().get(patientLoc).then().statusCode(200);

        principal.setTenancyId("admin-tenant");
        given().when().get(patientLoc).then().statusCode(404);
    }

    @Test
    void get_enrollment_succeeds_for_cross_tenant_admin() {
        UUID[] ids = createTrialAndSiteIds();
        UUID trialId = ids[0], siteId = ids[1];

        String patientLoc = given()
            .contentType("application/json")
            .body("{\"patientId\":\"PAT-BYPASS-001\"}")
            .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId)
            .then().statusCode(201).extract().header("Location");

        principal.setTenancyId("other-tenant");
        principal.setCrossTenantAdmin(true);
        given().when().get(patientLoc).then().statusCode(200);
    }
}
