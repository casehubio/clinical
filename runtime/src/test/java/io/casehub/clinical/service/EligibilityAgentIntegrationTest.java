package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.spi.EligibilityCriteriaEvaluator;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class EligibilityAgentIntegrationTest {

    @Inject EligibilityCriteriaEvaluator evaluator;
    @Inject FixedCurrentPrincipal principal;
    @InjectMock AgentProvider agentProvider;

    @BeforeEach
    void setup() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"criteria\":[{\"criterionId\":\"criterion-0\",\"met\":true,\"marginal\":false,\"reasoning\":\"ok\"}]}"),
                new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.001, 500L, 400L, "sess-1", 1, false)));
    }

    @Test
    void cdiDisplacement_llmImplIsActive() {
        assertInstanceOf(LlmEligibilityCriteriaEvaluator.class, evaluator);
    }

    @Test
    void evaluateAndScreen_viaRest_returnsCriteriaMet() {
        UUID[] ids = createTrialSitePatient();
        UUID trialId = ids[0], siteId = ids[1], enrollmentId = ids[2];

        given()
            .contentType("application/json")
            .body("{\"protocolCriteria\":[\"Age 18-65\"]}")
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/evaluate-and-screen", trialId, siteId, enrollmentId)
        .then()
            .statusCode(200)
            .body("enrollmentStatus", equalTo("ELIGIBLE"))
            .body("screeningResult", equalTo("CRITERIA_MET"));
    }

    @Test
    void evaluateAndScreen_llmReturnsMarginal_triggersIrbPath() {
        when(agentProvider.invoke(any())).thenReturn(Multi.createFrom().items(
                new AgentEvent.TextDelta("{\"criteria\":[{\"criterionId\":\"criterion-0\",\"met\":false,\"marginal\":true,\"reasoning\":\"borderline\"}]}"),
                new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.001, 500L, 400L, "sess-2", 1, false)));

        UUID[] ids = createTrialSitePatient();
        UUID trialId = ids[0], siteId = ids[1], enrollmentId = ids[2];

        given()
            .contentType("application/json")
            .body("{\"protocolCriteria\":[\"ECOG 0-1\"]}")
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/evaluate-and-screen", trialId, siteId, enrollmentId)
        .then()
            .statusCode(200)
            .body("enrollmentStatus", equalTo("SCREENING"))
            .body("screeningResult", equalTo("MARGINAL"));
    }

    @Test
    void evaluateAndScreen_alreadyScreened_returns409() {
        UUID[] ids = createTrialSitePatient();
        UUID trialId = ids[0], siteId = ids[1], enrollmentId = ids[2];

        given()
            .contentType("application/json")
            .body("{\"protocolCriteria\":[\"Age 18-65\"]}")
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/evaluate-and-screen", trialId, siteId, enrollmentId)
        .then()
            .statusCode(200);

        given()
            .contentType("application/json")
            .body("{\"protocolCriteria\":[\"Age 18-65\"]}")
        .when()
            .post("/trials/{t}/sites/{s}/patients/{e}/evaluate-and-screen", trialId, siteId, enrollmentId)
        .then()
            .statusCode(409);
    }

    private UUID[] createTrialSitePatient() {
        String trialLoc = given()
            .contentType("application/json")
            .body("{\"protocolId\":\"ELIG-AGENT-" + UUID.randomUUID() + "\",\"phase\":\"PHASE_I\",\"sponsor\":\"T\",\"targetEnrollment\":5}")
        .when().post("/trials").then().statusCode(201).extract().header("Location");
        UUID trialId = UUID.fromString(trialLoc.substring(trialLoc.lastIndexOf('/') + 1));

        String siteLoc = given()
            .contentType("application/json")
            .body("{\"investigatorId\":\"pi-elig-agent\"}")
        .when().post("/trials/{id}/sites", trialId).then().statusCode(201).extract().header("Location");
        UUID siteId = UUID.fromString(siteLoc.substring(siteLoc.lastIndexOf('/') + 1));

        String patientLoc = given()
            .contentType("application/json")
            .body("{\"patientId\":\"PAT-ELIG-" + UUID.randomUUID() + "\"}")
        .when().post("/trials/{t}/sites/{s}/patients", trialId, siteId).then().statusCode(201).extract().header("Location");
        UUID enrollmentId = UUID.fromString(patientLoc.substring(patientLoc.lastIndexOf('/') + 1));

        return new UUID[]{trialId, siteId, enrollmentId};
    }
}
