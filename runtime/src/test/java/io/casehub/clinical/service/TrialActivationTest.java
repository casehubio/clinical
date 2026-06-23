package io.casehub.clinical.service;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class TrialActivationTest {

    @Inject TrialActivationService trialActivationService;
    @Inject FixedCurrentPrincipal principal;

    @AfterEach
    void resetPrincipal() { principal.reset(); }

    @Test
    void activation_persists_engine_case_id_and_sets_status_active() {
        UUID trialId = createTrial();

        trialActivationService.activate(trialId);

        await().atMost(3, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    ClinicalTrial trial = findTrial(trialId);
                    assertThat(trial.status.name()).isEqualTo("ACTIVE");
                    assertThat(trial.engineCaseId).as("engineCaseId set after activation").isNotNull();
                });
    }

    @Test
    void activation_endpoint_returns_204() {
        UUID trialId = createTrial();

        given()
            .when().post("/trials/" + trialId + "/activate")
            .then().statusCode(204);

        await().atMost(3, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> {
                    ClinicalTrial trial = findTrial(trialId);
                    assertThat(trial.engineCaseId).isNotNull();
                });
    }

    @Test
    void activating_unknown_trial_returns_404() {
        given().when().post("/trials/" + UUID.randomUUID() + "/activate").then().statusCode(404);
    }

    @Test
    void activating_already_active_trial_returns_409() {
        UUID trialId = createTrial();
        given().when().post("/trials/" + trialId + "/activate").then().statusCode(204);

        await().atMost(3, SECONDS).pollInterval(100, MILLISECONDS)
                .untilAsserted(() -> assertThat(findTrial(trialId).engineCaseId).isNotNull());

        given().when().post("/trials/" + trialId + "/activate").then().statusCode(409);
    }

    @Test
    void activating_wrong_tenant_trial_returns_404() {
        UUID trialId = createTrial();
        principal.setTenancyId("other-tenant");
        given().when().post("/trials/" + trialId + "/activate").then().statusCode(404);
    }

    @Test
    void activating_cross_tenant_trial_succeeds_for_admin() {
        UUID trialId = createTrial();
        principal.setTenancyId("other-tenant");
        principal.setCrossTenantAdmin(true);
        given().when().post("/trials/" + trialId + "/activate").then().statusCode(204);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID createTrial() {
        String location = given()
                .contentType("application/json")
                .body("""
                    {
                      "protocolId": "TRIAL-ACT-001",
                      "phase": "PHASE_III",
                      "sponsor": "TestSponsor",
                      "targetEnrollment": 100
                    }
                    """)
                .when().post("/trials")
                .then().statusCode(201)
                .extract().header("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);
        return UUID.fromString(id);
    }

    @Transactional
    ClinicalTrial findTrial(UUID id) {
        return ClinicalTrial.findById(id);
    }
}
