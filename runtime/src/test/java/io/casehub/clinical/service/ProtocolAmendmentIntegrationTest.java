package io.casehub.clinical.service;

import io.casehub.api.model.CaseStatus;
import io.casehub.clinical.ledger.ProtocolAmendmentLedgerEntry;
import io.casehub.clinical.support.EngineStateCleaner;
import io.casehub.engine.common.spi.cache.CaseInstanceCache;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class ProtocolAmendmentIntegrationTest {

    @Inject LedgerEntryRepository ledgerRepo;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject EngineStateCleaner engineStateCleaner;

    /**
     * Wait for in-flight engine cases from prior test classes to finish their STARTING phase,
     * then clear all engine in-memory state. Prevents "CaseInstance not found or wrong tenant"
     * races in InMemoryCaseInstanceRepository when prior test classes' cases (e.g.
     * ClinicalLayerComplianceTest) remain active and interfere with new startCase() calls.
     */
    @BeforeEach
    void waitForEngineQuiesceBefore() {
        await().atMost(15, SECONDS).until(() ->
            caseInstanceCache.getAll().stream()
                .noneMatch(ci -> ci.getState() == CaseStatus.STARTING));
        engineStateCleaner.clearAll();
    }


    @Test
    void propose_creates_amendment_PROPOSED_and_writes_proposal_ledger_entry() {
        UUID trialId = UUID.randomUUID();
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
    void propose_then_await_APPROVED_and_writes_resolution_ledger_entry() {
        UUID trialId = UUID.randomUUID();
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
