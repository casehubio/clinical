package io.casehub.clinical.service;

import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class EligibilityScreeningIntegrationTest {

    @Inject FixedCurrentPrincipal principal;

    UUID trialId, siteId, enrollmentId;

    @AfterEach
    void resetPrincipal() { principal.reset(); }

    @BeforeEach
    @Transactional
    void setup() {
        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.trialId = trialId;
        site.investigatorId = "pi-screen-001";
        site.tenantId = principal.tenancyId();
        site.persist();

        PatientEnrollment e = new PatientEnrollment();
        e.id = enrollmentId;
        e.siteId = siteId;
        e.tenantId = principal.tenancyId();
        e.patientId = "P-SCREEN-001";
        e.persist();
    }

    @Test
    void screen_MARGINAL_sets_SCREENING_status() {
        given()
            .contentType("application/json")
            .body("""
                { "criteria": [
                  { "id": "c7", "met": false, "marginal": true },
                  { "id": "c11", "met": false, "marginal": true }
                ]}
                """)
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/screen", trialId, siteId, enrollmentId)
        .then()
            .statusCode(200)
            .body("enrollmentStatus", equalTo("SCREENING"))
            .body("screeningResult", equalTo("MARGINAL"));
    }

    @Test
    void screen_CRITERIA_MET_sets_ELIGIBLE_status() {
        given()
            .contentType("application/json")
            .body("""
                { "criteria": [
                  { "id": "c1", "met": true, "marginal": false }
                ]}
                """)
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/screen", trialId, siteId, enrollmentId)
        .then()
            .statusCode(200)
            .body("enrollmentStatus", equalTo("ELIGIBLE"));
    }

    @Test
    void screen_EXCLUDED_sets_INELIGIBLE_status() {
        given()
            .contentType("application/json")
            .body("""
                { "criteria": [
                  { "id": "c1", "met": false, "marginal": false }
                ]}
                """)
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/screen", trialId, siteId, enrollmentId)
        .then()
            .statusCode(200)
            .body("enrollmentStatus", equalTo("INELIGIBLE"));
    }
}
