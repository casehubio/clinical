package io.casehub.clinical.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SiteResourceTest {

    private String createTrial() {
        return given()
            .contentType("application/json")
            .body("""
                {"protocolId":"SITE-TEST","phase":"PHASE_II","sponsor":"Test","targetEnrollment":10}
                """)
        .when()
            .post("/trials")
        .then()
            .statusCode(201)
            .extract().header("Location");
    }

    @Test
    void post_site_returns_201_with_location() {
        String trialLocation = createTrial();
        UUID trialId = UUID.fromString(trialLocation.substring(trialLocation.lastIndexOf('/') + 1));

        given()
            .contentType("application/json")
            .body("""
                {"investigatorId": "pi-alice-001"}
                """)
        .when()
            .post("/trials/{id}/sites", trialId)
        .then()
            .statusCode(201)
            .header("Location", containsString("/sites/"));
    }

    @Test
    void get_site_returns_investigator_id_and_status() {
        String trialLocation = createTrial();
        UUID trialId = UUID.fromString(trialLocation.substring(trialLocation.lastIndexOf('/') + 1));

        String siteLocation =
            given()
                .contentType("application/json")
                .body("{\"investigatorId\": \"pi-bob-002\"}")
            .when()
                .post("/trials/{id}/sites", trialId)
            .then()
                .statusCode(201)
                .extract().header("Location");

        given()
        .when()
            .get(siteLocation)
        .then()
            .statusCode(200)
            .body("investigatorId", equalTo("pi-bob-002"))
            .body("status", equalTo("PENDING"));
    }

    @Test
    void post_site_to_unknown_trial_returns_404() {
        given()
            .contentType("application/json")
            .body("{\"investigatorId\": \"pi-x\"}")
        .when()
            .post("/trials/{id}/sites", UUID.randomUUID())
        .then()
            .statusCode(404);
    }

    @Test
    void get_unknown_site_returns_404() {
        String trialLocation = createTrial();
        UUID trialId = UUID.fromString(trialLocation.substring(trialLocation.lastIndexOf('/') + 1));

        given()
        .when()
            .get("/trials/{trialId}/sites/{siteId}", trialId, UUID.randomUUID())
        .then()
            .statusCode(404);
    }
}
