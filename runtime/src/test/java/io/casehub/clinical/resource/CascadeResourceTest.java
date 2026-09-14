package io.casehub.clinical.resource;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {"SPONSOR", "INVESTIGATOR", "COORDINATOR"})
class CascadeResourceTest {

    @Inject CurrentPrincipal principal;

    private UUID aeId;
    private UUID aeIdGrade1;

    @BeforeEach
    @Transactional
    void setup() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "TEST-CASCADE-" + UUID.randomUUID().toString().substring(0, 8);
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "test-sponsor";
        trial.status = TrialStatus.ENROLLING;
        trial.tenantId = principal.tenancyId();
        trial.persist();

        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trial.id;
        site.investigatorId = "test-investigator";
        site.tenantId = principal.tenancyId();
        site.persist();

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.siteId = site.id;
        enrollment.patientId = "P-" + UUID.randomUUID().toString().substring(0, 8);
        enrollment.enrollmentStatus = EnrollmentStatus.ENROLLED;
        enrollment.consentStatus = ConsentStatus.OBTAINED;
        enrollment.tenantId = principal.tenancyId();
        enrollment.persist();

        AdverseEvent ae4 = new AdverseEvent();
        ae4.id = UUID.randomUUID();
        ae4.enrollmentId = enrollment.id;
        ae4.grade = CtcaeGrade.GRADE_4;
        ae4.actuality = EventActuality.ACTUAL;
        ae4.reportedAt = Instant.now();
        ae4.occurredAt = Instant.now();
        ae4.slaDeadline = Instant.now().plusSeconds(86400);
        ae4.tenantId = principal.tenancyId();
        ae4.persist();
        aeId = ae4.id;

        AdverseEvent ae1 = new AdverseEvent();
        ae1.id = UUID.randomUUID();
        ae1.enrollmentId = enrollment.id;
        ae1.grade = CtcaeGrade.GRADE_1;
        ae1.actuality = EventActuality.ACTUAL;
        ae1.reportedAt = Instant.now();
        ae1.occurredAt = Instant.now();
        ae1.slaDeadline = Instant.now().plusSeconds(604800);
        ae1.tenantId = principal.tenancyId();
        ae1.persist();
        aeIdGrade1 = ae1.id;
    }

    @Test
    void grade4ReturnsEscalationTemplate() {
        given()
            .when().get("/api/adverse-events/" + aeId + "/cascade")
            .then()
            .statusCode(200)
            .body("size()", equalTo(8))
            .body("[0].step", equalTo("AE_REPORTED"))
            .body("[0].status", equalTo("COMPLETED"))
            .body("[1].step", equalTo("SLA_ASSIGNED"))
            .body("[1].status", equalTo("COMPLETED"))
            .body("[2].step", equalTo("ESCALATION_CASE_STARTED"))
            .body("[2].status", equalTo("PENDING"));
    }

    @Test
    void grade1ReturnsShortTemplate() {
        given()
            .when().get("/api/adverse-events/" + aeIdGrade1 + "/cascade")
            .then()
            .statusCode(200)
            .body("size()", equalTo(3))
            .body("[0].step", equalTo("AE_REPORTED"))
            .body("[0].status", equalTo("COMPLETED"))
            .body("[2].step", equalTo("LEDGER_SEALED"));
    }

    @Test
    void unknownAeReturns404() {
        given()
            .when().get("/api/adverse-events/" + UUID.randomUUID() + "/cascade")
            .then()
            .statusCode(404);
    }
}
