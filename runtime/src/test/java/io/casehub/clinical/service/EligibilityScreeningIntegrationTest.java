package io.casehub.clinical.service;

import io.casehub.api.model.CaseStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class EligibilityScreeningIntegrationTest {

    @Inject FixedCurrentPrincipal principal;
    @Inject CaseInstanceCache caseInstanceCache;

    UUID trialId, siteId, enrollmentId;

    /**
     * After each test, wait for any async engine cases started by the test to finish their
     * STARTING phase. This prevents the @InjectMock CaseInstanceRepository in
     * ProtocolAmendmentListenerTest (which runs after this class) from intercepting
     * in-flight case start operations and causing "CaseInstance not found" errors.
     */
    @AfterEach
    void waitForEngineQuiesceAndResetPrincipal() {
        await().atMost(10, SECONDS).until(() ->
            caseInstanceCache.getAll().stream()
                .noneMatch(ci -> ci.getState() == CaseStatus.STARTING));
        principal.reset();
    }

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

    @Test
    void screen_empty_criteria_returns_400() {
        given()
            .contentType("application/json")
            .body("{ \"criteria\": [] }")
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/screen", trialId, siteId, enrollmentId)
        .then()
            .statusCode(400);
    }
}
