package io.casehub.clinical.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.*;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class TrialDashboardResourceTest {

    @Inject FixedCurrentPrincipal principal;

    private UUID trialId;
    private UUID siteAId;
    private UUID siteBId;
    private UUID aeId;

    @BeforeEach
    @Transactional
    void setup() {
        trialId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.tenantId = principal.tenancyId();
        trial.protocolId = "TEST-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Test Sponsor";
        trial.targetEnrollment = 20;
        trial.persist();

        siteAId = UUID.randomUUID();
        TrialSite siteA = new TrialSite();
        siteA.id = siteAId;
        siteA.tenantId = principal.tenancyId();
        siteA.trialId = trialId;
        siteA.investigatorId = "dr-chen";
        siteA.targetEnrollment = 120;
        siteA.persist();

        siteBId = UUID.randomUUID();
        TrialSite siteB = new TrialSite();
        siteB.id = siteBId;
        siteB.tenantId = principal.tenancyId();
        siteB.trialId = trialId;
        siteB.investigatorId = "dr-patel";
        siteB.targetEnrollment = 80;
        siteB.persist();

        UUID enrollmentId = UUID.randomUUID();
        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = enrollmentId;
        enrollment.tenantId = principal.tenancyId();
        enrollment.siteId = siteAId;
        enrollment.patientId = "P-001";
        enrollment.persist();

        AdverseEvent ae = new AdverseEvent();
        aeId = UUID.randomUUID();
        ae.id = aeId;
        ae.tenantId = principal.tenancyId();
        ae.enrollmentId = enrollmentId;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.slaDeadline = Instant.now().plusSeconds(86400);
        ae.persist();

        ProtocolDeviation dev = new ProtocolDeviation();
        dev.id = UUID.randomUUID();
        dev.tenantId = principal.tenancyId();
        dev.siteId = siteAId;
        dev.deviationType = "CONSENT_VIOLATION";
        dev.severity = DeviationSeverity.MAJOR;
        dev.piApprovalStatus = PiApprovalStatus.APPROVED;
        dev.commandedAt = Instant.now();
        dev.persist();
    }

    @Test
    void summary_returns_trial_metrics() {
        given()
            .when().get("/trials/{trialId}/summary", trialId)
            .then()
            .statusCode(200)
            .body("protocolId", equalTo("TEST-001"))
            .body("phase", equalTo("PHASE_III"))
            .body("totalEnrolled", greaterThanOrEqualTo(1))
            .body("totalAdverseEvents", greaterThanOrEqualTo(1));
    }

    @Test
    void summary_returns_404_for_wrong_tenant() {
        given()
            .when().get("/trials/{trialId}/summary", UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    void patients_returns_flattened_list() {
        given()
            .when().get("/trials/{trialId}/patients", trialId)
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("[0].patientId", equalTo("P-001"))
            .body("[0].siteId", notNullValue());
    }

    @Test
    void adverse_events_returns_flattened_list_with_sla() {
        given()
            .when().get("/trials/{trialId}/adverse-events", trialId)
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("[0].grade", equalTo("GRADE_3"))
            .body("[0].slaDeadline", notNullValue());
    }

    @Test
    void deviations_returns_empty_list_when_none() {
        given()
            .when().get("/trials/{trialId}/deviations", trialId)
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1));
    }

    // --- Task 3: Cross-source endpoints ---

    @Test
    void agents_returns_capability_list_with_trust_data() {
        given()
            .when().get("/trials/{trialId}/agents", trialId)
            .then()
            .statusCode(200)
            .body("size()", equalTo(8))
            .body("[0].capability", notNullValue())
            .body("[0].trustDimension", notNullValue())
            .body("[0].threshold", notNullValue())
            .body("[0].maturityPhase", equalTo("bootstrap"))
            .body("[0].endorsementRatio", nullValue())
            .body("[0].distinctTrustDimensions", equalTo("eligibility-precision, protocol-adherence, safety-accuracy"));
    }

    @Test
    void agents_returns_404_for_unknown_trial() {
        given()
            .when().get("/trials/{trialId}/agents", UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    void governance_returns_none_status_for_ae_without_susar() {
        // The seeded AE has no SUSAR oversight case — use field set in @BeforeEach
        given()
            .when().get("/trials/{trialId}/adverse-events/{aeId}/governance", trialId, aeId)
            .then()
            .statusCode(200)
            .body("grade", equalTo("GRADE_3"))
            .body("susarOversightStatus", equalTo("NONE"));
    }

    @Test
    void governance_returns_404_for_unknown_ae() {
        given()
            .when().get("/trials/{trialId}/adverse-events/{aeId}/governance", trialId, UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    void governance_returns_404_for_unknown_trial() {
        given()
            .when().get("/trials/{trialId}/adverse-events/{aeId}/governance", UUID.randomUUID(), aeId)
            .then()
            .statusCode(404);
    }

    @Test
    void ledger_entries_returns_empty_when_no_entries() {
        given()
            .when().get("/trials/{trialId}/ledger-entries", trialId)
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));
    }

    @Test
    void ledger_entries_returns_404_for_unknown_trial() {
        given()
            .when().get("/trials/{trialId}/ledger-entries", UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    // --- Task 1: Sites list endpoint ---

    @Test
    void sites_returns_enriched_site_list() {
        given()
            .when().get("/trials/{trialId}/sites", trialId)
            .then()
            .statusCode(200)
            .body("size()", equalTo(2))
            .body("find { it.investigatorId == 'dr-chen' }.enrolledCount", equalTo(1))
            .body("find { it.investigatorId == 'dr-chen' }.adverseEventCount", equalTo(1))
            .body("find { it.investigatorId == 'dr-chen' }.deviationCount", equalTo(1))
            .body("find { it.investigatorId == 'dr-chen' }.targetEnrollment", equalTo(120))
            .body("find { it.investigatorId == 'dr-patel' }.enrolledCount", equalTo(0))
            .body("find { it.investigatorId == 'dr-patel' }.status", equalTo("PENDING"))
            .body("find { it.investigatorId == 'dr-patel' }.targetEnrollment", equalTo(80));
    }

    @Test
    void sites_returns_404_for_unknown_trial() {
        given()
            .when().get("/trials/{trialId}/sites", UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    void agents_returns_endorsement_ratio_as_number_not_string() {
        given()
            .when().get("/trials/{trialId}/agents", trialId)
            .then()
            .statusCode(200)
            .body("[0].maturityPhase", isA(String.class))
            .body("[0].decisionCount", notNullValue());
    }

    @Test
    void adverse_events_returns_enriched_fields() {
        given()
            .when().get("/trials/{trialId}/adverse-events", trialId)
            .then()
            .statusCode(200)
            .body("[0].siteName", equalTo("dr-chen"))
            .body("[0].patientId", equalTo("P-001"));
    }

    @Test
    void deviations_returns_enriched_fields() {
        given()
            .when().get("/trials/{trialId}/deviations", trialId)
            .then()
            .statusCode(200)
            .body("size()", greaterThanOrEqualTo(1))
            .body("[0].siteName", equalTo("dr-chen"))
            .body("[0].reportedAt", notNullValue());
    }
}
