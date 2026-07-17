package io.casehub.clinical.cbr;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SiteStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration test for CBR precedent retrieval REST endpoints.
 * <p>
 * Pre-populates CBR store with known cases, persists entities, then queries
 * via REST and verifies response structure and content.
 */
@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
class PrecedentEndpointTest {

    @Inject ClinicalCbrService cbrService;
    @Inject FixedCurrentPrincipal principal;

    private UUID trialId;
    private UUID siteId;
    private UUID enrollmentId;
    private UUID aeId;
    private UUID deviationId;
    private UUID amendmentId;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up
        AdverseEvent.deleteAll();
        ProtocolDeviation.deleteAll();
        ProtocolAmendment.deleteAll();
        PatientEnrollment.deleteAll();
        TrialSite.deleteAll();
        ClinicalTrial.deleteAll();

        // Create trial hierarchy
        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();

        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.tenantId = principal.tenancyId();
        trial.protocolId = "PRECEDENT-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Test Pharma";
        trial.targetEnrollment = 100;
        trial.status = TrialStatus.ACTIVE;
        trial.persist();

        TrialSite site = new TrialSite();
        site.id = siteId;
        site.tenantId = principal.tenancyId();
        site.trialId = trialId;
        site.investigatorId = "dr-test";
        site.status = SiteStatus.ACTIVE;
        site.targetEnrollment = 50;
        site.persist();

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = enrollmentId;
        enrollment.tenantId = principal.tenancyId();
        enrollment.siteId = siteId;
        enrollment.patientId = "PAT-001";
        enrollment.consentStatus = ConsentStatus.OBTAINED;
        enrollment.enrollmentStatus = EnrollmentStatus.ENROLLED;
        enrollment.persist();

        // Create AE
        aeId = UUID.randomUUID();
        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.tenantId = principal.tenancyId();
        ae.enrollmentId = enrollmentId;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.actuality = EventActuality.ACTUAL;
        ae.eventType = "Neutropenia";
        ae.suspected = true;
        ae.unexpected = true;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.FILED;
        ae.susarOversightStatus = SusarOversightStatus.COMPLETED;
        ae.engineCaseId = UUID.randomUUID();
        ae.occurredAt = Instant.now().minusSeconds(7200);
        ae.reportedAt = Instant.now().minusSeconds(3600);
        ae.persist();

        // Create deviation
        deviationId = UUID.randomUUID();
        ProtocolDeviation deviation = new ProtocolDeviation();
        deviation.id = deviationId;
        deviation.tenantId = principal.tenancyId();
        deviation.siteId = siteId;
        deviation.deviationType = "CONSENT_TIMING_DELAY";
        deviation.severity = DeviationSeverity.MINOR;
        deviation.escalationRequirement = EscalationRequirement.NONE;
        deviation.piApprovalStatus = PiApprovalStatus.APPROVED;
        deviation.engineCaseId = UUID.randomUUID();
        deviation.commandedAt = Instant.now().minusSeconds(1800);
        deviation.persist();

        // Create amendment
        amendmentId = UUID.randomUUID();
        ProtocolAmendment amendment = new ProtocolAmendment();
        amendment.id = amendmentId;
        amendment.tenantId = principal.tenancyId();
        amendment.trialId = trialId;
        amendment.proposedChange = "Add imaging endpoint";
        amendment.status = ProtocolAmendmentStatus.PROPOSED;
        amendment.amendmentCaseStatus = AmendmentCaseStatus.NONE;
        amendment.proposedAt = Instant.now().minusSeconds(900);
        amendment.persist();

        // Pre-populate CBR store with precedent cases
        populateAePrecedents();
        populateDeviationPrecedents();
        populateAmendmentPrecedents();
    }

    private void populateAePrecedents() {
        for (int i = 0; i < 3; i++) {
            List<PlanTrace> traces = List.of(
                    new PlanTrace("safety-review", "safety-monitoring", "officer-alpha", "COMPLETED", 1, Map.of())
                                            );

            PlanCbrCase cbrCase = new PlanCbrCase(
                    "Grade 3 Neutropenia in PHASE_III trial, unexpected=true, suspected=true",
                    "Safety review: CONTINUE_MONITORING. DSMB escalated: false. IND report: true. SUSAR oversight: true.",
                    "COMPLETED",
                    1.0,
                    FeatureValue.toFeatureMap(Map.ofEntries(
                            Map.entry("grade", 3),
                            Map.entry("eventType", "Neutropenia"),
                            Map.entry("trialPhase", "PHASE_III"),
                            Map.entry("unexpected", "true"),
                            Map.entry("suspected", "true"),
                            Map.entry("treatmentArm", "ARM_A"),
                            Map.entry("priorAeCount", "MULTIPLE"),
                            Map.entry("safetyReviewOutcome", "CONTINUE_MONITORING"),
                            Map.entry("dsmbEscalated", "false"),
                            Map.entry("indReportFiled", "true"),
                            Map.entry("susarOversight", "true")
                                                           )),
                    traces
            );

            cbrService.storeIdempotent(
                    cbrCase,
                    "clinical-ae",
                    "ae-precedent-" + i,
                    ClinicalCbrDomains.AE,
                    principal.tenancyId(),
                    null
                                      );
        }
    }

    private void populateDeviationPrecedents() {
        // Store 2 deviation precedents with plan traces
        for (int i = 0; i < 2; i++) {
            List<PlanTrace> planTrace = List.of(
                new PlanTrace("pi-oversight", "pi-authorisation", "pi-smith", "APPROVED", 1, Map.of())
            );

            PlanCbrCase cbrCase = new PlanCbrCase(
                "CONSENT_TIMING_DELAY deviation, severity: MINOR",
                "PI decision: APPROVED, IRB decision: N/A",
                "RESOLVED",
                1.0,
                FeatureValue.toFeatureMap(Map.of(
                    "deviationType", "CONSENT_TIMING_DELAY",
                    "severity", "MINOR",
                    "escalationRequirement", "NONE",
                    "piDecision", "APPROVED",
                    "irbDecision", "N/A"
                )),
                planTrace
            );

            cbrService.storeIdempotent(
                cbrCase,
                "clinical-deviation",
                "deviation-precedent-" + i,
                ClinicalCbrDomains.DEVIATION,
                principal.tenancyId(),
                null
            );
        }
    }

    private void populateAmendmentPrecedents() {
        // Store 2 amendment precedents (textual, no features)
        for (int i = 0; i < 2; i++) {
            TextualCbrCase cbrCase = new TextualCbrCase(
                "Add imaging endpoint to protocol",
                "Advisor recommended: APPROVE_WITH_CONDITIONS",
                "APPROVED",
                1.0
            );

            cbrService.storeIdempotent(
                cbrCase,
                "clinical-amendment",
                "amendment-precedent-" + i,
                ClinicalCbrDomains.AMENDMENT,
                principal.tenancyId(),
                null
            );
        }
    }

    @Test
    void aePrecedents_returnsMatchingCases() {
        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/trials/{trialId}/adverse-events/{aeId}/precedents", trialId, aeId)
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0))
                .body("[0].score", notNullValue())
                .body("[0].grade", equalTo("GRADE_3"))
                .body("[0].eventType", equalTo("Neutropenia"))
                .body("[0].trialPhase", equalTo("PHASE_III"))
                .body("[0].unexpected", equalTo(true))
                .body("[0].suspected", equalTo(true))
                .body("[0].treatmentArm", equalTo("ARM_A"))
                .body("[0].priorAeCount", equalTo("MULTIPLE"))
                .body("[0].safetyReviewOutcome", notNullValue())
                .body("[0].dsmbEscalated", notNullValue())
                .body("[0].indReportFiled", notNullValue())
                .body("[0].susarOversight", notNullValue())
                .body("[0].steps", hasSize(1))
                .body("[0].steps[0].bindingName", equalTo("safety-review"))
                .body("[0].steps[0].capabilityName", equalTo("safety-monitoring"))
                .body("[0].steps[0].workerName", equalTo("officer-alpha"))
                .body("[0].steps[0].stepOutcome", equalTo("COMPLETED"))
                .body("[0].problem", notNullValue())
                .body("[0].outcome", notNullValue());
    }

    @Test
    void aePrecedents_aeNotFound_returns404() {
        UUID nonExistentAeId = UUID.randomUUID();
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/trials/{trialId}/adverse-events/{aeId}/precedents", trialId, nonExistentAeId)
        .then()
            .statusCode(404);
    }

    @Test
    void deviationPrecedents_returnsMatchingCases() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/trials/{trialId}/deviations/{devId}/precedents", trialId, deviationId)
        .then()
            .statusCode(200)
            .body("size()", greaterThan(0))
            .body("[0].score", notNullValue())
            .body("[0].deviationType", equalTo("CONSENT_TIMING_DELAY"))
            .body("[0].severity", equalTo("MINOR"))
            .body("[0].escalationRequirement", equalTo("NONE"))
            .body("[0].piDecision", equalTo("APPROVED"))
            .body("[0].irbDecision", equalTo("N/A"))
            .body("[0].steps", hasSize(1))
            .body("[0].steps[0].bindingName", equalTo("pi-oversight"))
            .body("[0].steps[0].capabilityName", equalTo("pi-authorisation"))
            .body("[0].steps[0].stepOutcome", equalTo("APPROVED"))
            .body("[0].problem", notNullValue())
            .body("[0].outcome", notNullValue());
    }

    @Test
    void deviationPrecedents_deviationNotFound_returns404() {
        UUID nonExistentDevId = UUID.randomUUID();
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/trials/{trialId}/deviations/{devId}/precedents", trialId, nonExistentDevId)
        .then()
            .statusCode(404);
    }

    @Test
    void amendmentPrecedents_returnsMatchingCases() {
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/trials/{trialId}/amendments/{amendmentId}/precedents", trialId, amendmentId)
        .then()
            .statusCode(200)
            .body("size()", greaterThan(0))
            .body("[0].score", equalTo(1.0f))  // Phase 1 all score 1.0
            .body("[0].proposedChange", notNullValue())
            .body("[0].advisorOutcome", notNullValue())
            .body("[0].outcome", notNullValue());
    }

    @Test
    void amendmentPrecedents_amendmentNotFound_returns404() {
        UUID nonExistentAmendmentId = UUID.randomUUID();
        given()
            .contentType(ContentType.JSON)
        .when()
            .get("/trials/{trialId}/amendments/{amendmentId}/precedents", trialId, nonExistentAmendmentId)
        .then()
            .statusCode(404);
    }
}
