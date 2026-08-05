package io.casehub.clinical.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.casehub.clinical.api.ClinicalGroups.*;
import static io.restassured.RestAssured.given;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {SPONSOR, INVESTIGATOR, COORDINATOR})
class PatientResourceRegradeValidationTest {

    @Test
    void regrade_nullReason_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"grade\":\"GRADE_3\",\"reason\":null}")
            .post("/trials/00000000-0000-0000-0000-000000000001/sites/00000000-0000-0000-0000-000000000002/patients/00000000-0000-0000-0000-000000000003/adverse-events/00000000-0000-0000-0000-000000000004/regrade")
            .then()
            .statusCode(400);
    }

    @Test
    void regrade_blankReason_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"grade\":\"GRADE_3\",\"reason\":\"  \"}")
            .post("/trials/00000000-0000-0000-0000-000000000001/sites/00000000-0000-0000-0000-000000000002/patients/00000000-0000-0000-0000-000000000003/adverse-events/00000000-0000-0000-0000-000000000004/regrade")
            .then()
            .statusCode(400);
    }

    @Test
    void regrade_oversizeReason_returns400() {
        String longReason = "x".repeat(501);
        given()
            .contentType(ContentType.JSON)
            .body("{\"grade\":\"GRADE_3\",\"reason\":\"" + longReason + "\"}")
            .post("/trials/00000000-0000-0000-0000-000000000001/sites/00000000-0000-0000-0000-000000000002/patients/00000000-0000-0000-0000-000000000003/adverse-events/00000000-0000-0000-0000-000000000004/regrade")
            .then()
            .statusCode(400);
    }
}
