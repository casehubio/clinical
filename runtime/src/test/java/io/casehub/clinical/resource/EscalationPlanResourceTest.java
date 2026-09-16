package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
        ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
class EscalationPlanResourceTest {
    @jakarta.inject.Inject
    jakarta.persistence.EntityManager em;


    private UUID aeId;

    @BeforeEach
    @Transactional
    void setup() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "PROTO-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Sponsor";
        trial.tenantId = "test-tenant";
        em.persist(trial);

        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trial.id;
        site.investigatorId = "pi-1";
        site.tenantId = "test-tenant";
        em.persist(site);

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.siteId = site.id;
        enrollment.patientId = "P001";
        enrollment.tenantId = "test-tenant";
        em.persist(enrollment);

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = enrollment.id;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.eventType = "hepatotoxicity";
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.tenantId = "test-tenant";
        em.persist(ae);
        aeId = ae.id;
    }

    @Test
    void getEscalationPlans_existingAe_returns200() {
        given()
                .when().get("/api/adverse-events/" + aeId + "/escalation-plans")
                .then()
                .statusCode(200)
                .body("retrievedCaseCount", org.hamcrest.Matchers.greaterThanOrEqualTo(0));
    }

    @Test
    void getEscalationPlans_unknownAe_returns404() {
        given()
                .when().get("/api/adverse-events/" + UUID.randomUUID() + "/escalation-plans")
                .then()
                .statusCode(404);
    }
}
