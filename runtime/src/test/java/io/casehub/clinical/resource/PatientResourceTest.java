package io.casehub.clinical.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PatientResourceTest {

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
}
