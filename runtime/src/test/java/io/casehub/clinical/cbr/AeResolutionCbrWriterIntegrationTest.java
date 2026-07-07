package io.casehub.clinical.cbr;

import io.casehub.clinical.api.AeEscalationCompletedEvent;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureVectorCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link AeResolutionCbrWriter}.
 * <p>
 * Verifies end-to-end CBR case storage: persists trial+site+enrollment+AE entities,
 * calls the writer directly, and queries the CBR store to confirm the stored case
 * has correct features and metadata.
 */
@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class AeResolutionCbrWriterIntegrationTest {

    @Inject AeResolutionCbrWriter writer;
    @Inject CbrCaseMemoryStore store;
    @Inject FixedCurrentPrincipal principal;

    private UUID trialId;
    private UUID siteId;
    private UUID enrollmentId;

    @BeforeEach
    @Transactional
    void setUp() {
        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();

        // Persist trial
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.tenantId = principal.tenancyId();
        trial.protocolId = "CBR-INT-001";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "CBR Pharma";
        trial.targetEnrollment = 100;
        trial.status = TrialStatus.ACTIVE;
        trial.persist();

        // Persist site
        TrialSite site = new TrialSite();
        site.id = siteId;
        site.tenantId = principal.tenancyId();
        site.trialId = trialId;
        site.investigatorId = "dr-cbr@v1";
        site.status = SiteStatus.ACTIVE;
        site.persist();

        // Persist enrollment
        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = enrollmentId;
        enrollment.tenantId = principal.tenancyId();
        enrollment.siteId = siteId;
        enrollment.patientId = "CBR-PATIENT-001";
        enrollment.consentStatus = ConsentStatus.OBTAINED;
        enrollment.enrollmentStatus = EnrollmentStatus.ENROLLED;
        enrollment.persist();
    }

    private UUID persistAe(CtcaeGrade grade, String eventType, RegulatorySubmissionStatus regStatus, SusarOversightStatus susarStatus) {
        UUID aeId = UUID.randomUUID();
        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.tenantId = principal.tenancyId();
        ae.enrollmentId = enrollmentId;
        ae.grade = grade;
        ae.actuality = EventActuality.ACTUAL;
        ae.eventType = eventType;
        ae.suspected = true;
        ae.regulatorySubmissionStatus = regStatus;
        ae.susarOversightStatus = susarStatus;
        ae.engineCaseId = UUID.randomUUID();
        ae.occurredAt = Instant.now().minusSeconds(3600);
        ae.reportedAt = Instant.now().minusSeconds(1800);
        ae.persist();
        return aeId;
    }

    @Test
    @Transactional
    void onAeEscalationCompleted_storesFeatureVectorCbrCaseWithCorrectFeatures() {
        // Arrange
        UUID aeId = persistAe(CtcaeGrade.GRADE_3, "Neutropenia", RegulatorySubmissionStatus.FILED, SusarOversightStatus.COMPLETED);

        AeEscalationCompletedEvent event = new AeEscalationCompletedEvent(
            aeId,
            CtcaeGrade.GRADE_3,
            siteId,
            "CONTINUE_MONITORING",
            false,  // dsmbEscalated
            Instant.now(),
            true    // unexpected
        );

        // Act
        writer.onAeEscalationCompleted(event);

        // Assert: query the CBR store
        CbrQuery query = CbrQuery.of(
            principal.tenancyId(),
            ClinicalCbrDomains.AE,
            "clinical-ae",
            Map.of("grade", 3.0),
            10
        );

        List<ScoredCbrCase<FeatureVectorCbrCase>> results = store.retrieveSimilar(query, FeatureVectorCbrCase.class);
        assertThat(results).isNotEmpty();

        // Find our specific case by checking for the exact feature combination
        FeatureVectorCbrCase storedCase = results.stream()
            .map(ScoredCbrCase::cbrCase)
            .filter(c -> {
                Map<String, Object> f = c.features();
                return f.get("grade").equals(3)
                    && f.get("eventType").equals("Neutropenia")
                    && f.get("safetyReviewOutcome").equals("CONTINUE_MONITORING")
                    && f.get("dsmbEscalated").equals(false);
            })
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected case not found in results"));

        assertThat(storedCase.problem()).contains("Grade 3", "Neutropenia", "PHASE_III", "unexpected=true", "suspected=true");
        assertThat(storedCase.solution()).contains("CONTINUE_MONITORING", "DSMB escalated: false", "IND report: true", "SUSAR oversight: true");
        assertThat(storedCase.outcome()).isEqualTo("COMPLETED");
        assertThat(storedCase.confidence()).isEqualTo(1.0);

        Map<String, Object> features = storedCase.features();
        assertThat(features.get("grade")).isEqualTo(3);  // GRADE_3.ordinal() + 1
        assertThat(features.get("eventType")).isEqualTo("Neutropenia");
        assertThat(features.get("trialPhase")).isEqualTo("PHASE_III");
        assertThat(features.get("unexpected")).isEqualTo(true);
        assertThat(features.get("suspected")).isEqualTo(true);
        assertThat(features.get("safetyReviewOutcome")).isEqualTo("CONTINUE_MONITORING");
        assertThat(features.get("dsmbEscalated")).isEqualTo(false);
        assertThat(features.get("indReportFiled")).isEqualTo(true);
        assertThat(features.get("susarOversight")).isEqualTo(true);
    }

    @Test
    @Transactional
    void onAeEscalationCompleted_handlesNullEventType() {
        // Arrange
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, null, RegulatorySubmissionStatus.FILED, SusarOversightStatus.COMPLETED);

        AeEscalationCompletedEvent event = new AeEscalationCompletedEvent(
            aeId,
            CtcaeGrade.GRADE_4,
            siteId,
            "SUSPEND_ENROLLMENT",
            true,   // dsmbEscalated
            Instant.now(),
            false   // unexpected
        );

        // Act
        writer.onAeEscalationCompleted(event);

        // Assert
        CbrQuery query = CbrQuery.of(
            principal.tenancyId(),
            ClinicalCbrDomains.AE,
            "clinical-ae",
            Map.of("grade", 4.0),
            10
        );

        List<ScoredCbrCase<FeatureVectorCbrCase>> results = store.retrieveSimilar(query, FeatureVectorCbrCase.class);
        assertThat(results).isNotEmpty();

        // Find our specific case
        FeatureVectorCbrCase storedCase = results.stream()
            .map(ScoredCbrCase::cbrCase)
            .filter(c -> {
                Map<String, Object> f = c.features();
                return f.get("grade").equals(4)
                    && f.get("eventType").equals("UNKNOWN")
                    && f.get("safetyReviewOutcome").equals("SUSPEND_ENROLLMENT");
            })
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected case not found in results"));

        Map<String, Object> features = storedCase.features();
        assertThat(features.get("eventType")).isEqualTo("UNKNOWN");
        assertThat(storedCase.problem()).contains("UNKNOWN");
    }

    @Test
    @Transactional
    void onAeEscalationCompleted_handlesNullSafetyReviewOutcome() {
        // Arrange
        UUID aeId = persistAe(CtcaeGrade.GRADE_3, "Neutropenia", RegulatorySubmissionStatus.FILED, SusarOversightStatus.COMPLETED);

        AeEscalationCompletedEvent event = new AeEscalationCompletedEvent(
            aeId,
            CtcaeGrade.GRADE_3,
            siteId,
            null,   // safetyReviewOutcome
            false,
            Instant.now(),
            true
        );

        // Act
        writer.onAeEscalationCompleted(event);

        // Assert
        CbrQuery query = CbrQuery.of(
            principal.tenancyId(),
            ClinicalCbrDomains.AE,
            "clinical-ae",
            Map.of("grade", 3.0),
            10
        );

        List<ScoredCbrCase<FeatureVectorCbrCase>> results = store.retrieveSimilar(query, FeatureVectorCbrCase.class);
        assertThat(results).isNotEmpty();

        // Find our specific case
        FeatureVectorCbrCase storedCase = results.stream()
            .map(ScoredCbrCase::cbrCase)
            .filter(c -> {
                Map<String, Object> f = c.features();
                return f.get("grade").equals(3)
                    && f.get("eventType").equals("Neutropenia")
                    && f.get("safetyReviewOutcome").equals("UNKNOWN")
                    && f.get("dsmbEscalated").equals(false);
            })
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected case not found in results"));

        Map<String, Object> features = storedCase.features();
        assertThat(features.get("safetyReviewOutcome")).isEqualTo("UNKNOWN");
        assertThat(storedCase.solution()).contains("UNKNOWN");
    }

    @Test
    @Transactional
    void onAeEscalationCompleted_grade5_createsCorrectFeatureVector() {
        // Arrange
        UUID aeId = persistAe(CtcaeGrade.GRADE_5, "Neutropenia", RegulatorySubmissionStatus.FILED, SusarOversightStatus.COMPLETED);

        AeEscalationCompletedEvent event = new AeEscalationCompletedEvent(
            aeId,
            CtcaeGrade.GRADE_5,
            siteId,
            "TRIAL_SUSPENSION_RECOMMENDED",
            true,   // dsmbEscalated
            Instant.now(),
            true    // unexpected
        );

        // Act
        writer.onAeEscalationCompleted(event);

        // Assert
        CbrQuery query = CbrQuery.of(
            principal.tenancyId(),
            ClinicalCbrDomains.AE,
            "clinical-ae",
            Map.of("grade", 5.0),
            10
        );

        List<ScoredCbrCase<FeatureVectorCbrCase>> results = store.retrieveSimilar(query, FeatureVectorCbrCase.class);
        assertThat(results).isNotEmpty();

        // Find our specific case
        FeatureVectorCbrCase storedCase = results.stream()
            .map(ScoredCbrCase::cbrCase)
            .filter(c -> {
                Map<String, Object> f = c.features();
                return f.get("grade").equals(5)
                    && f.get("eventType").equals("Neutropenia")
                    && f.get("safetyReviewOutcome").equals("TRIAL_SUSPENSION_RECOMMENDED")
                    && f.get("dsmbEscalated").equals(true);
            })
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected case not found in results"));

        Map<String, Object> features = storedCase.features();
        assertThat(features.get("grade")).isEqualTo(5);  // GRADE_5.ordinal() + 1
        assertThat(storedCase.problem()).contains("Grade 5");
        assertThat(storedCase.solution()).contains("TRIAL_SUSPENSION_RECOMMENDED");
    }
}
