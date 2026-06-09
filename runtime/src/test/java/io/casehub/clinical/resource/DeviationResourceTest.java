package io.casehub.clinical.resource;

import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class DeviationResourceTest {

    @Inject FixedCurrentPrincipal principal;

    @AfterEach
    void resetPrincipal() { principal.reset(); }

    /** Creates a trial and site via REST, returns {trialId, siteId}. */
    private UUID[] createTrialAndSite() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"DEV-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"S\",\"targetEnrollment\":5}")
            .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        String siteLoc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-res\"}")
            .when().post("/trials/{id}/sites", trialId).then().statusCode(201).extract().header("Location");
        UUID siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));

        return new UUID[]{trialId, siteId};
    }

    @Test
    void reportDeviationReturns201WithLocation() {
        UUID[] ids = createTrialAndSite();
        UUID trialId = ids[0], siteId = ids[1];

        var response = given()
            .contentType("application/json")
            .body("{\"deviationType\":\"sample-window\",\"severity\":\"MINOR\"}")
            .when().post("/trials/{t}/sites/{s}/deviations", trialId, siteId)
            .then().statusCode(201)
            .header("Location", containsString("/deviations/"))
            .body("piApprovalStatus", is("COMMANDED"))
            .body("responseDeadline", notNullValue())
            .extract().response();

        String location = response.header("Location");
        String deviationId = location.substring(location.lastIndexOf('/') + 1);

        given().when()
            .get("/trials/{t}/sites/{s}/deviations/{d}", trialId, siteId, deviationId)
            .then().statusCode(200)
            .body("piApprovalStatus", is("COMMANDED"))
            .body("escalationRequirement", is("NONE"));
    }

    @Test
    void reportDeviationToWrongSiteReturns404() {
        UUID[] ids = createTrialAndSite();
        UUID trialId = ids[0];

        given()
            .contentType("application/json")
            .body("{\"deviationType\":\"x\",\"severity\":\"MINOR\"}")
            .when().post("/trials/{t}/sites/{s}/deviations", trialId, UUID.randomUUID())
            .then().statusCode(404);
    }

    @Test
    void reportDeviationMissingSeverityReturns400() {
        UUID[] ids = createTrialAndSite();
        UUID trialId = ids[0], siteId = ids[1];

        given()
            .contentType("application/json")
            .body("{\"deviationType\":\"x\"}")
            .when().post("/trials/{t}/sites/{s}/deviations", trialId, siteId)
            .then().statusCode(400);
    }

    @Test
    void getUnknownDeviationReturns404() {
        UUID[] ids = createTrialAndSite();
        UUID trialId = ids[0], siteId = ids[1];

        given().when()
            .get("/trials/{t}/sites/{s}/deviations/{d}", trialId, siteId, UUID.randomUUID())
            .then().statusCode(404);
    }

    @Test
    void get_deviation_returns_404_for_wrong_tenant() {
        UUID[] ids = createTrialAndSite();
        UUID trialId = ids[0], siteId = ids[1];

        var resp = given()
            .contentType("application/json")
            .body("{\"deviationType\":\"consent-gap\",\"severity\":\"MINOR\"}")
            .when().post("/trials/{t}/sites/{s}/deviations", trialId, siteId)
            .then().statusCode(201).extract();
        String location = resp.header("Location");
        String deviationId = location.substring(location.lastIndexOf('/') + 1);

        principal.setTenancyId("other-tenant");
        given().when().get("/trials/{t}/sites/{s}/deviations/{d}", trialId, siteId, deviationId)
            .then().statusCode(404);
    }

    @Test
    void get_deviation_succeeds_for_cross_tenant_admin() {
        UUID[] ids = createTrialAndSite();
        UUID trialId = ids[0], siteId = ids[1];

        var resp = given()
            .contentType("application/json")
            .body("{\"deviationType\":\"consent-gap\",\"severity\":\"MINOR\"}")
            .when().post("/trials/{t}/sites/{s}/deviations", trialId, siteId)
            .then().statusCode(201).extract();
        String location = resp.header("Location");
        String deviationId = location.substring(location.lastIndexOf('/') + 1);

        principal.setTenancyId("other-tenant");
        principal.setCrossTenantAdmin(true);
        given().when().get("/trials/{t}/sites/{s}/deviations/{d}", trialId, siteId, deviationId)
            .then().statusCode(200);
    }

    @Test
    void deviation_inherits_site_tenantId_not_principal_tenantId() {
        UUID[] ids = createTrialAndSite();
        UUID trialId = ids[0], siteId = ids[1];

        principal.setTenancyId("admin-tenant");
        principal.setCrossTenantAdmin(true);
        var resp = given()
            .contentType("application/json")
            .body("{\"deviationType\":\"sample-window\",\"severity\":\"MINOR\"}")
            .when().post("/trials/{t}/sites/{s}/deviations", trialId, siteId)
            .then().statusCode(201).extract();
        String location = resp.header("Location");
        String deviationId = location.substring(location.lastIndexOf('/') + 1);

        principal.reset();
        given().when().get("/trials/{t}/sites/{s}/deviations/{d}", trialId, siteId, deviationId)
            .then().statusCode(200);

        principal.setTenancyId("admin-tenant");
        given().when().get("/trials/{t}/sites/{s}/deviations/{d}", trialId, siteId, deviationId)
            .then().statusCode(404);
    }
}
