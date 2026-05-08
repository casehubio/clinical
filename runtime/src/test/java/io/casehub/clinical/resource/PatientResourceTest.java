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
        UUID siteId = createTrialAndSite();

        // Get trialId from the site
        // Use a fresh trial+site with known trialId via separate helper
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
}
