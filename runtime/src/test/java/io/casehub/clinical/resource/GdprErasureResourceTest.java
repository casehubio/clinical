package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class GdprErasureResourceTest {

    @Inject FixedCurrentPrincipal principal;

    @Test
    @TestSecurity(user = "sponsor-user", roles = {ClinicalGroups.SPONSOR})
    void delete_returns_204_and_erases_enrollment() {
        String patientId = "ERASURE-" + UUID.randomUUID();
        persistEnrollment(patientId);

        given()
            .when().delete("/api/gdpr/erasure/patients/{patientId}", patientId)
            .then()
            .statusCode(204)
            .header("X-Enrollments-Erased", "1");
    }

    @Test
    @TestSecurity(user = "coordinator-user", roles = {ClinicalGroups.COORDINATOR})
    void delete_returns_204_for_coordinator() {
        String patientId = "ERASURE-COORD-" + UUID.randomUUID();
        persistEnrollment(patientId);

        given()
            .when().delete("/api/gdpr/erasure/patients/{patientId}", patientId)
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "sponsor-user", roles = {ClinicalGroups.SPONSOR})
    void delete_returns_404_for_unknown_patient() {
        given()
            .when().delete("/api/gdpr/erasure/patients/{patientId}", "NONEXISTENT-" + UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "pi-user", roles = {ClinicalGroups.INVESTIGATOR})
    void delete_returns_403_for_investigator() {
        given()
            .when().delete("/api/gdpr/erasure/patients/{patientId}", "ANY")
            .then()
            .statusCode(403);
    }

    @Transactional
    void persistEnrollment(String patientId) {
        PatientEnrollment e = new PatientEnrollment();
        e.id = UUID.randomUUID();
        e.siteId = UUID.randomUUID();
        e.patientId = patientId;
        e.tenantId = principal.tenancyId();
        e.consentStatus = ConsentStatus.PENDING;
        e.enrollmentStatus = EnrollmentStatus.CANDIDATE;
        e.persist();
    }
}
