package io.casehub.clinical.resource;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.AeGradeChange;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.casehub.clinical.api.ClinicalGroups.COORDINATOR;
import static io.casehub.clinical.api.ClinicalGroups.INVESTIGATOR;
import static io.casehub.clinical.api.ClinicalGroups.SPONSOR;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {SPONSOR, INVESTIGATOR, COORDINATOR})
class AeRegradeResourceTest {

    @Inject FixedCurrentPrincipal principal;
    @Inject
            jakarta.persistence.EntityManager em;


    private UUID trialId, siteId, enrollmentId, aeId;

    @BeforeEach
    @Transactional
    void setup() {
        em.createQuery("DELETE FROM AeGradeChange").executeUpdate();
        em.createQuery("DELETE FROM AdverseEvent").executeUpdate();
        em.createQuery("DELETE FROM PatientEnrollment").executeUpdate();
        em.createQuery("DELETE FROM TrialSite").executeUpdate();
        em.createQuery("DELETE FROM ClinicalTrial").executeUpdate();

        trialId = UUID.randomUUID();
        ClinicalTrial trial = new ClinicalTrial();
        trial.id               = trialId;
        trial.protocolId       = "PROTO-001";
        trial.sponsor          = "TestSponsor";
        trial.phase            = io.casehub.clinical.api.model.TrialPhase.PHASE_III;
        trial.targetEnrollment = 100;
        trial.tenantId         = principal.tenancyId();
        em.persist(trial);

        siteId = UUID.randomUUID();
        TrialSite site = new TrialSite();
        site.id             = siteId;
        site.trialId        = trialId;
        site.investigatorId = "inv-1";
        site.tenantId       = principal.tenancyId();
        em.persist(site);

        enrollmentId = UUID.randomUUID();
        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id        = enrollmentId;
        enrollment.siteId    = siteId;
        enrollment.patientId = "P-001";
        enrollment.tenantId  = principal.tenancyId();
        em.persist(enrollment);

        aeId = UUID.randomUUID();
        AdverseEvent ae = new AdverseEvent();
        ae.id           = aeId;
        ae.enrollmentId = enrollmentId;
        ae.grade        = CtcaeGrade.GRADE_1;
        ae.occurredAt   = Instant.now().minus(Duration.ofHours(2));
        ae.reportedAt   = Instant.now().minus(Duration.ofHours(1));
        ae.slaDeadline  = ae.reportedAt.plus(Duration.ofDays(7));
        ae.tenantId     = principal.tenancyId();
        em.persist(ae);}

    @Test
    void regrade_updatesGrade() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"grade\": \"GRADE_3\", \"reason\": \"Condition worsened\"}")
            .post("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/adverse-events/{aeId}/regrade",
                trialId, siteId, enrollmentId, aeId)
            .then()
            .statusCode(200)
            .body("grade", equalTo("GRADE_3"));
    }

    @Test
    void regrade_nonexistentAe_returns404() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"grade\": \"GRADE_3\", \"reason\": \"test\"}")
            .post("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/adverse-events/{aeId}/regrade",
                trialId, siteId, enrollmentId, UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    void gradeHistory_returnsOrderedEntries() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"grade\": \"GRADE_2\", \"reason\": \"Moderate\"}")
            .post("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/adverse-events/{aeId}/regrade",
                trialId, siteId, enrollmentId, aeId);

        given()
            .contentType(ContentType.JSON)
            .body("{\"grade\": \"GRADE_3\", \"reason\": \"Severe\"}")
            .post("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/adverse-events/{aeId}/regrade",
                trialId, siteId, enrollmentId, aeId);

        given()
            .get("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/adverse-events/{aeId}/grade-history",
                trialId, siteId, enrollmentId, aeId)
            .then()
            .statusCode(200)
            .body("$.size()", equalTo(2))
            .body("[0].newGrade", equalTo("GRADE_2"))
            .body("[1].newGrade", equalTo("GRADE_3"));
    }

    @Test
    void gradeHistory_nonexistentAe_returns404() {
        given()
            .get("/trials/{trialId}/sites/{siteId}/patients/{enrollmentId}/adverse-events/{aeId}/grade-history",
                trialId, siteId, enrollmentId, UUID.randomUUID())
            .then()
            .statusCode(404);
    }
}
