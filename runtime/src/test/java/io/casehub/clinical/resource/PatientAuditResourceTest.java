package io.casehub.clinical.resource;

import static io.restassured.RestAssured.given;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class PatientAuditResourceTest {

    @Test
    void prov_endpoint_returns_404_for_enrollment_with_no_ledger_entries() {
        UUID trialId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID enrollmentId = persistEnrollment(siteId);

        given()
        .when()
            .get("/trials/{t}/sites/{s}/patients/{e}/audit/prov", trialId, siteId, enrollmentId)
        .then()
            .statusCode(404);
    }

    @Test
    void merkle_proof_endpoint_returns_404_for_unknown_entry() {
        UUID trialId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID enrollmentId = persistEnrollment(siteId);
        UUID unknownEntryId = UUID.randomUUID();

        given()
        .when()
            .get("/trials/{t}/sites/{s}/patients/{e}/audit/entries/{id}/proof",
                    trialId, siteId, enrollmentId, unknownEntryId)
        .then()
            .statusCode(404);
    }

    @Transactional
    UUID persistEnrollment(UUID siteId) {
        PatientEnrollment e = new PatientEnrollment();
        e.id = UUID.randomUUID();
        e.siteId = siteId;
        e.patientId = "test-patient";
        e.tenantId = "default";
        e.consentStatus = ConsentStatus.PENDING;
        e.enrollmentStatus = EnrollmentStatus.CANDIDATE;
        e.persist();
        return e.id;
    }
}
