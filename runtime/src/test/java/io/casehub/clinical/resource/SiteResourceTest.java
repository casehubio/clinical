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
class SiteResourceTest {

    @Inject FixedCurrentPrincipal principal;

    @AfterEach
    void resetPrincipal() { principal.reset(); }

    private String createTrial() {
        return given()
            .contentType("application/json")
            .body("{\"protocolId\":\"SITE-TEST-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_II\",\"sponsor\":\"Test\",\"targetEnrollment\":10}")
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

    @Test
    void get_site_returns_404_for_wrong_tenant() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ISO-S-001\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        String siteLoc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-iso\"}")
            .when().post("/trials/{id}/sites", trialId).then().statusCode(201).extract().header("Location");
        UUID siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));

        principal.setTenancyId("other-tenant");
        given().when().get("/trials/{t}/sites/{s}", trialId, siteId).then().statusCode(404);
    }

    @Test
    void add_site_returns_404_when_trial_belongs_to_different_tenant() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ISO-S-002\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        principal.setTenancyId("other-tenant");
        given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-iso\"}")
            .when().post("/trials/{id}/sites", trialId)
            .then().statusCode(404);
    }

    @Test
    void get_site_succeeds_for_cross_tenant_admin() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ISO-S-004\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        String siteLoc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-iso\"}")
            .when().post("/trials/{id}/sites", trialId).then().statusCode(201).extract().header("Location");
        UUID siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));

        principal.setTenancyId("other-tenant");
        principal.setCrossTenantAdmin(true);
        given().when().get("/trials/{t}/sites/{s}", trialId, siteId).then().statusCode(200);
    }

    @Test
    void site_inherits_trial_tenantId_not_principal_tenantId() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ISO-S-003\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        // cross-tenant admin adds a site to a different tenant's trial
        principal.setTenancyId("admin-tenant");
        principal.setCrossTenantAdmin(true);
        String siteLoc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-iso\"}")
            .when().post("/trials/{id}/sites", trialId).then().statusCode(201).extract().header("Location");
        UUID siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));

        // default tenant can find the site (site.tenantId = trial.tenantId = default tenant)
        principal.reset();
        given().when().get("/trials/{t}/sites/{s}", trialId, siteId).then().statusCode(200);

        // admin's own tenant (no bypass) cannot find it — proves site.tenantId != "admin-tenant"
        principal.setTenancyId("admin-tenant");
        given().when().get("/trials/{t}/sites/{s}", trialId, siteId).then().statusCode(404);
    }

    @Test
    void post_site_with_target_enrollment_persists_value() {
        String trialLocation = createTrial();
        UUID trialId = UUID.fromString(trialLocation.substring(trialLocation.lastIndexOf('/') + 1));

        String siteLocation =
            given()
                .contentType("application/json")
                .body("""
                    {"investigatorId": "pi-target-001", "targetEnrollment": 150}
                    """)
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
            .body("investigatorId", equalTo("pi-target-001"))
            .body("targetEnrollment", equalTo(150));
    }
}
