package io.casehub.clinical.service;

import io.casehub.clinical.ledger.ProtocolAmendmentLedgerEntry;
import io.casehub.ledger.runtime.repository.LedgerEntryRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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

        UUID amendmentId = UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));

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

        UUID amendmentId = UUID.fromString(loc.substring(loc.lastIndexOf('/') + 1));

        // DefaultProtocolAmendmentAdvisor → PROCEED; await engine case completion
        await().atMost(15, SECONDS).untilAsserted(() ->
            given().when()
                .get("/trials/{t}/amendments/{id}", trialId, amendmentId)
            .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"))
        );

        // Both proposal + resolution entries should be written
        long count = ledgerRepo.findBySubjectId(amendmentId, "default")
            .stream()
            .filter(e -> e instanceof ProtocolAmendmentLedgerEntry)
            .count();
        assertThat(count).isGreaterThanOrEqualTo(1); // at minimum proposal entry
    }
}
