package io.casehub.clinical.service;

import io.casehub.api.model.CaseStatus;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.ledger.ProtocolAmendmentLedgerEntry;
import io.casehub.clinical.support.EngineStateCleaner;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProtocolAmendmentIntegrationTest {

    @Inject LedgerEntryRepository ledgerRepo;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject EngineStateCleaner engineStateCleaner;
    @Inject FixedCurrentPrincipal principal;

    UUID trialId;

    @BeforeEach
    void setup() {
        engineStateCleaner.cancelAllAndClear();
        persistTestData();
    }

    @Transactional
    void persistTestData() {
        trialId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.protocolId = "PA-INT-TEST-" + UUID.randomUUID();
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Test Sponsor";
        trial.targetEnrollment = 100;
        trial.tenantId = principal.tenancyId();
        trial.persist();
    }

    @Test
    @Order(2)
    void propose_creates_amendment_PROPOSED_and_writes_proposal_ledger_entry() {
        String loc = given()
            .contentType("application/json")
            .body("{\"proposedChange\": \"Dose escalation v2\"}")
        .when()
            .post("/trials/{t}/amendments", trialId)
        .then()
            .statusCode(201)
            .body("status", equalTo("PROPOSED"))
            .extract().header("Location");

        UUID amendmentId = extractId(loc);

        // Proposal ledger entry written synchronously in same TX as persist
        long count = ledgerRepo.findBySubjectId(amendmentId, "default")
            .stream()
            .filter(e -> e instanceof ProtocolAmendmentLedgerEntry)
            .count();
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(1)
    void propose_then_await_APPROVED_and_writes_resolution_ledger_entry() {
        String loc = given()
            .contentType("application/json")
            .body("{\"proposedChange\": \"Endpoint amendment\"}")
        .when()
            .post("/trials/{t}/amendments", trialId)
        .then()
            .statusCode(201)
            .extract().header("Location");

        UUID amendmentId = extractId(loc);

        // DefaultProtocolAmendmentAdvisor → PROCEED; await engine case completion
        await().atMost(15, SECONDS).untilAsserted(() ->
            given().when()
                .get("/trials/{t}/amendments/{id}", trialId, amendmentId)
            .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"))
        );

        // status=APPROVED is confirmed above → writeResolutionEntry committed → at least 2 entries
        long count = ledgerRepo.findBySubjectId(amendmentId, "default")
            .stream()
            .filter(e -> e instanceof ProtocolAmendmentLedgerEntry)
            .count();
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    private UUID extractId(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}
