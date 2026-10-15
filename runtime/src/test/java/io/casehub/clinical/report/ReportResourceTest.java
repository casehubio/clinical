package io.casehub.clinical.report;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.MONITOR})
class ReportResourceTest {

    private static final UUID TRIAL_ID = UUID.fromString("316e3846-4ea7-3b18-a6f7-e01ce6582a69");

    @Inject EntityManager em;
    @Inject CurrentPrincipal principal;

    @BeforeEach
    @Transactional
    void createReportTrial() {
        if (em.find(ClinicalTrial.class, TRIAL_ID) != null) return;
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = TRIAL_ID;
        trial.protocolId = "REPORT-TRIAL";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Test sponsor";
        trial.tenantId = principal.tenancyId();
        em.persist(trial);
    }

    @Test
    void indSafetyReturnsJson() {
        given()
            .queryParam("trialId", TRIAL_ID)
            .queryParam("from", "2026-01-01")
            .queryParam("to", "2026-06-30")
            .accept("application/json")
        .when()
            .get("/api/reports/ind-safety")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("metadata.reportType", equalTo("ind-safety"))
            .body("period", notNullValue())
            .body("icsrs", notNullValue());
    }

    @Test
    void auditTrailReturnsJson() {
        given()
            .queryParam("trialId", TRIAL_ID)
            .accept("application/json")
        .when()
            .get("/api/reports/audit-trail")
        .then()
            .statusCode(200);
    }

    @Test
    void complianceReturnsJson() {
        given()
            .queryParam("trialId", TRIAL_ID)
            .accept("application/json")
        .when()
            .get("/api/reports/compliance")
        .then()
            .statusCode(200);
    }

    @Test
    void merkleVerificationReturnsJson() {
        given()
            .queryParam("trialId", TRIAL_ID)
        .when()
            .get("/api/reports/merkle-verification")
        .then()
            .statusCode(200)
            .contentType("application/json");
    }

    @Test
    @TestSecurity(user = "investigator", roles = {ClinicalGroups.INVESTIGATOR})
    void rejectsInvestigatorRole() {
        given()
            .queryParam("trialId", TRIAL_ID)
            .queryParam("from", "2026-01-01")
            .queryParam("to", "2026-06-30")
        .when()
            .get("/api/reports/ind-safety")
        .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "anonymous")
    void rejectsUnauthenticated() {
        given()
            .queryParam("trialId", TRIAL_ID)
            .queryParam("from", "2026-01-01")
            .queryParam("to", "2026-06-30")
        .when()
            .get("/api/reports/ind-safety")
        .then()
            .statusCode(anyOf(is(401), is(403)));
    }
}
