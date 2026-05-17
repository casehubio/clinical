package io.casehub.clinical.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class DeviationResourceTest {

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
}
