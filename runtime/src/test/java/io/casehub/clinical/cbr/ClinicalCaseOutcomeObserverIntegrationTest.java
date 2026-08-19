package io.casehub.clinical.cbr;

import io.casehub.api.spi.CaseOutcomeEvent;
import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SiteStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR})
class ClinicalCaseOutcomeObserverIntegrationTest {

    @Inject ClinicalCaseOutcomeObserver observer;
    @Inject ClinicalCbrService cbrService;
    @Inject FixedCurrentPrincipal principal;

    private UUID trialId, siteId, enrollmentId, aeId;

    @BeforeEach
    @Transactional
    void setUp() {
        AdverseEvent.deleteAll();
        PatientEnrollment.deleteAll();
        TrialSite.deleteAll();
        ClinicalTrial.deleteAll();

        trialId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        enrollmentId = UUID.randomUUID();
        aeId = UUID.randomUUID();

        ClinicalTrial trial = new ClinicalTrial();
        trial.id = trialId;
        trial.tenantId = principal.tenancyId();
        trial.protocolId = "CBR-INT-001";
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
        enrollment.patientId = "PAT-CBR";
        enrollment.consentStatus = ConsentStatus.OBTAINED;
        enrollment.enrollmentStatus = EnrollmentStatus.ENROLLED;
        enrollment.treatmentArm = "ARM_A";
        enrollment.persist();

        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.tenantId = principal.tenancyId();
        ae.enrollmentId = enrollmentId;
        ae.grade = CtcaeGrade.GRADE_3;
        ae.eventType = "Neutropenia";
        ae.suspected = true;
        ae.unexpected = true;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.FILED;
        ae.susarOversightStatus = SusarOversightStatus.COMPLETED;
        ae.engineCaseId = UUID.randomUUID();
        ae.occurredAt = Instant.now().minusSeconds(3600);
        ae.reportedAt = Instant.now().minusSeconds(1800);
        ae.persist();
    }

    @Test
    void onOutcome_storesPlanCbrCaseWithFeatures() {
        UUID caseId = UUID.randomUUID();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("aeId", aeId.toString());
        snapshot.put("safetyReview", "CONTINUE_MONITORING");
        snapshot.put("dsmbEscalation", "false");

        CaseOutcomeEvent event = new CaseOutcomeEvent(
            "ae-escalation", principal.tenancyId(), caseId, snapshot,
            "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        CbrQuery query = CbrQuery.of(principal.tenancyId(), ClinicalCbrDomains.AE,
            io.casehub.platform.api.path.Path.of(trialId.toString(), siteId.toString(), "PAT-CBR"), "clinical-ae", Map.of(), 10);
        List<ScoredCbrCase<PlanCbrCase>> results = cbrService.retrieveSimilar(query, PlanCbrCase.class);

        assertThat(results).isNotEmpty();
        PlanCbrCase stored = results.get(0).cbrCase();
        assertThat(stored.features()).containsKey("grade");
        assertThat(stored.features()).containsKey("eventType");
        assertThat(stored.problem()).contains("Grade 3", "Neutropenia");
        assertThat(stored.outcome()).isEqualTo("COMPLETED");
    }

    @Test
    void onOutcome_nonAeCase_doesNotStoreCbrCase() {
        UUID                caseId      = UUID.randomUUID();
        UUID                deviationId = UUID.randomUUID();
        Map<String, Object> snapshot    = new HashMap<>();
        snapshot.put("deviationId", deviationId.toString());

        Instant before = Instant.now();

        CaseOutcomeEvent event = new CaseOutcomeEvent(
                "protocol-deviation", principal.tenancyId(), caseId, snapshot,
                "COMPLETED", Instant.now(), Map.of());

        observer.onOutcome(event);

        CbrQuery query = CbrQuery.of(principal.tenancyId(), ClinicalCbrDomains.AE,
                                     io.casehub.platform.api.path.Path.of(trialId.toString(), siteId.toString(), "PAT-CBR"), "clinical-ae", Map.of(), 10)
                                 .withNotBefore(before);
        List<ScoredCbrCase<PlanCbrCase>> results = cbrService.retrieveSimilar(query, PlanCbrCase.class);
        assertThat(results).isEmpty();
    }
}
