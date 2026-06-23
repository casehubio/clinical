package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
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
class TrialResourceTest {

    @Inject FixedCurrentPrincipal principal;

    @AfterEach
    void resetPrincipal() { principal.reset(); }

    @Test
    void get_returns_404_for_wrong_tenant() {
        String location = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ISO-T-001\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        principal.setTenancyId("other-tenant");
        given().when().get("/trials/{id}", id).then().statusCode(404);
    }

    @Test
    void patch_sponsor_config_returns_404_for_wrong_tenant() {
        String location = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ISO-T-002\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        principal.setTenancyId("other-tenant");
        given()
            .contentType("application/json")
            .body("{\"connectorId\":\"slack\",\"destination\":\"https://example.com\"}")
            .when().patch("/trials/{id}/sponsor-config", id)
            .then().statusCode(404);
    }

    @Test
    void get_succeeds_for_cross_tenant_admin() {
        String location = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ISO-T-003\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        principal.setTenancyId("other-tenant");
        principal.setCrossTenantAdmin(true);
        given().when().get("/trials/{id}", id).then().statusCode(200);
    }

    @Test
    void post_trial_returns_201_with_location() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId": "ONCOL-001",
                  "phase": "PHASE_III",
                  "sponsor": "Acme Pharma",
                  "targetEnrollment": 150
                }
                """)
        .when()
            .post("/trials")
        .then()
            .statusCode(201)
            .header("Location", containsString("/trials/"));
    }

    @Test
    void get_trial_returns_200_with_fields() {
        String location =
            given()
                .contentType("application/json")
                .body("""
                    {
                      "protocolId": "ONCOL-002",
                      "phase": "PHASE_II",
                      "sponsor": "BioTest",
                      "targetEnrollment": 50
                    }
                    """)
            .when()
                .post("/trials")
            .then()
                .statusCode(201)
                .extract().header("Location");

        given()
        .when()
            .get(location)
        .then()
            .statusCode(200)
            .body("protocolId", equalTo("ONCOL-002"))
            .body("phase", equalTo("PHASE_II"))
            .body("sponsor", equalTo("BioTest"))
            .body("targetEnrollment", equalTo(50))
            .body("status", equalTo("PLANNING"))
            .body("sponsorNotificationConnectorId", nullValue())
            .body("sponsorNotificationDestination", nullValue());
    }

    @Test
    void get_unknown_trial_returns_404() {
        given()
        .when()
            .get("/trials/" + UUID.randomUUID())
        .then()
            .statusCode(404);
    }

    @Test
    void post_trial_missing_protocol_id_returns_400() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "phase": "PHASE_III",
                  "sponsor": "Acme",
                  "targetEnrollment": 100
                }
                """)
        .when()
            .post("/trials")
        .then()
            .statusCode(400);
    }

    @Test
    void post_trial_missing_phase_returns_400() {
        given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId": "ONCOL-NO-PHASE",
                  "sponsor": "Acme",
                  "targetEnrollment": 100
                }
                """)
        .when()
            .post("/trials")
        .then()
            .statusCode(400);
    }

    @Test
    void patch_sponsor_config_updates_both_fields() {
        String location = given()
            .contentType("application/json")
            .body("""
                {"protocolId":"PATCH-001","phase":"PHASE_II","sponsor":"Acme","targetEnrollment":10}
                """)
            .when().post("/trials")
            .then().statusCode(201)
            .extract().header("Location");

        given()
            .contentType("application/json")
            .body("""
                {"connectorId":"slack","destination":"https://hooks.slack.com/T999/B999/zzz"}
                """)
        .when()
            .patch(location + "/sponsor-config")
        .then()
            .statusCode(204);

        given().when().get(location)
            .then().statusCode(200)
            .body("sponsorNotificationConnectorId", equalTo("slack"))
            .body("sponsorNotificationDestination", equalTo("https://hooks.slack.com/T999/B999/zzz"));
    }

    @Test
    void patch_sponsor_config_with_nulls_clears_config() {
        String location = given()
            .contentType("application/json")
            .body("""
                {
                  "protocolId":"PATCH-002","phase":"PHASE_I","sponsor":"Bio","targetEnrollment":5,
                  "sponsorNotificationConnectorId":"slack",
                  "sponsorNotificationDestination":"https://hooks.slack.com/old"
                }
                """)
            .when().post("/trials")
            .then().statusCode(201)
            .extract().header("Location");

        given()
            .contentType("application/json")
            .body("""
                {"connectorId":null,"destination":null}
                """)
        .when()
            .patch(location + "/sponsor-config")
        .then()
            .statusCode(204);

        given().when().get(location)
            .then().statusCode(200)
            .body("sponsorNotificationConnectorId", nullValue())
            .body("sponsorNotificationDestination", nullValue());
    }

    @Test
    void patch_sponsor_config_unknown_trial_returns_404() {
        given()
            .contentType("application/json")
            .body("""
                {"connectorId":"slack","destination":"https://example.com"}
                """)
        .when()
            .patch("/trials/" + UUID.randomUUID() + "/sponsor-config")
        .then()
            .statusCode(404);
    }

    @Test
    void patch_sponsor_config_connector_id_too_long_returns_400() {
        String location = given()
            .contentType("application/json")
            .body("""
                {"protocolId":"PATCH-003","phase":"PHASE_III","sponsor":"Acme","targetEnrollment":20}
                """)
            .when().post("/trials")
            .then().statusCode(201)
            .extract().header("Location");

        given()
            .contentType("application/json")
            .body("{\"connectorId\":\"" + "x".repeat(65) + "\",\"destination\":\"https://example.com\"}")
        .when()
            .patch(location + "/sponsor-config")
        .then()
            .statusCode(400);
    }

    @Test
    void patch_sponsor_config_no_body_returns_400() {
        String location = given()
            .contentType("application/json")
            .body("""
                {"protocolId":"PATCH-004","phase":"PHASE_I","sponsor":"Acme","targetEnrollment":5}
                """)
            .when().post("/trials")
            .then().statusCode(201)
            .extract().header("Location");

        given()
            .contentType("application/json")
        .when()
            .patch(location + "/sponsor-config")
        .then()
            .statusCode(400);
    }

    @Test
    void patch_sponsor_config_destination_too_long_returns_400() {
        String location = given()
            .contentType("application/json")
            .body("""
                {"protocolId":"PATCH-005","phase":"PHASE_II","sponsor":"Acme","targetEnrollment":5}
                """)
            .when().post("/trials")
            .then().statusCode(201)
            .extract().header("Location");

        given()
            .contentType("application/json")
            .body("{\"connectorId\":\"slack\",\"destination\":\"https://example.com/" + "x".repeat(2048) + "\"}")
        .when()
            .patch(location + "/sponsor-config")
        .then()
            .statusCode(400);
    }

    @Test
    void register_with_sponsor_config_persists_connector_fields() {
        String body = """
            {
              "protocolId": "ONCO-2026-001",
              "phase": "PHASE_II",
              "sponsor": "Pfizer",
              "targetEnrollment": 50,
              "sponsorNotificationConnectorId": "slack",
              "sponsorNotificationDestination": "https://hooks.slack.com/T000/B000/xxx"
            }
            """;

        String location = given()
            .contentType("application/json")
            .body(body)
            .when().post("/trials")
            .then().statusCode(201)
            .extract().header("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);
        given()
            .when().get("/trials/" + id)
            .then().statusCode(200)
            .body("sponsorNotificationConnectorId", equalTo("slack"))
            .body("sponsorNotificationDestination", equalTo("https://hooks.slack.com/T000/B000/xxx"));
    }
}
