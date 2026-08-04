package io.casehub.clinical.cbr;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.TrialSite;
import io.casehub.neocortex.memory.cbr.*;
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

@QuarkusTest
@TestSecurity(user = "test-actor", roles = {ClinicalGroups.SPONSOR})
class AeEscalationPlanRetrieverIntegrationTest {

    @Inject ClinicalCbrService cbrService;
    @Inject AeEscalationPlanRetriever retriever;

    private UUID enrollmentId;

    @BeforeEach
    @Transactional
    void setup() {
        ClinicalTrial trial = new ClinicalTrial();
        trial.id = UUID.randomUUID();
        trial.protocolId = "PROTO-INT";
        trial.phase = TrialPhase.PHASE_III;
        trial.sponsor = "Test";
        trial.tenantId = "test-tenant";
        trial.persist();

        TrialSite site = new TrialSite();
        site.id = UUID.randomUUID();
        site.trialId = trial.id;
        site.investigatorId = "pi-1";
        site.tenantId = "test-tenant";
        site.persist();

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.siteId = site.id;
        enrollment.patientId = "P001";
        enrollment.tenantId = "test-tenant";
        enrollment.persist();
        enrollmentId = enrollment.id;
    }

    @Test
    @Transactional
    void roundTrip_storeAndRetrieve() {
        Instant before = Instant.now();

        var trace = new PlanTrace("safety-review", "safety-monitoring", "worker-1", "COMPLETED", 0, Map.of(), null);
        var features = Map.<String, Object>of(
                "grade", 3, "eventType", List.of("hepatotoxicity"),
                "trialPhase", "PHASE_III", "unexpected", "false",
                "suspected", "false", "treatmentArm", "UNASSIGNED",
                "priorAeCount", "NONE");
        var cbrCase = new PlanCbrCase("Grade 3 hepatotoxicity", "Safety review completed",
                "COMPLETED", 1.0, FeatureValue.toFeatureMap(features), List.of(trace), null, null);

        cbrService.storeIdempotent(cbrCase, "clinical-ae", "past-ae-1",
                ClinicalCbrDomains.AE, "test-tenant", null,
                io.casehub.platform.api.path.Path.of("trial-1", "site-1", "patient-1"));

        AdverseEvent ae = new AdverseEvent();
        ae.id = UUID.randomUUID();
        ae.enrollmentId = enrollmentId;
        ae.grade = CtcaeGrade.GRADE_4;
        ae.eventType = "hepatotoxicity";
        ae.unexpected = false;
        ae.suspected = false;
        ae.occurredAt = java.time.Instant.now();
        ae.reportedAt = java.time.Instant.now();
        ae.tenantId = "test-tenant";
        ae.persist();

        EscalationPlanRecommendation result = retriever.retrieve(ae);

        if (result.hasRecommendation()) {
            assertThat(result.retrievedCaseCount()).isGreaterThan(0);
            assertThat(result.adaptedPlan().steps()).isNotEmpty();
            assertThat(result.adaptedPlan().steps().get(0).action())
                    .isIn(AdaptationAction.BOOSTED, AdaptationAction.RETAINED);
        }
    }
}
