package io.casehub.clinical.cbr;

import io.casehub.clinical.api.model.*;
import io.casehub.clinical.entity.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AeCbrFeatureBuilderTest {

    @Test
    void buildFeatures_allFieldsPresent_returns14Features() {
        AdverseEvent ae = new AdverseEvent();
        ae.grade                      = CtcaeGrade.GRADE_3;
        ae.eventType                  = "Neutropenia";
        ae.suspected                  = true;
        ae.unexpected                 = true;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.FILED;
        ae.susarOversightStatus       = SusarOversightStatus.COMPLETED;

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.treatmentArm = "ARM_A";

        ClinicalTrial trial = new ClinicalTrial();
        trial.phase = TrialPhase.PHASE_III;

        var ctx = new AeCbrContext(ae, enrollment, trial, "CONTINUE_MONITORING",
                                   true, 2, 45, 100, 0.82);

        Map<String, Object> features = AeCbrFeatureBuilder.buildFeatures(ctx);

        assertThat(features).hasSize(14);
        assertThat(features.get("grade")).isEqualTo(3);
        assertThat(features.get("eventType")).isEqualTo(java.util.List.of("Neutropenia"));
        assertThat(features.get("trialPhase")).isEqualTo("PHASE_III");
        assertThat(features.get("unexpected")).isEqualTo("true");
        assertThat(features.get("suspected")).isEqualTo("true");
        assertThat(features.get("treatmentArm")).isEqualTo("ARM_A");
        assertThat(features.get("priorAeCount")).isEqualTo("MULTIPLE");
        assertThat(features.get("safetyReviewOutcome")).isEqualTo("CONTINUE_MONITORING");
        assertThat(features.get("dsmbEscalated")).isEqualTo("true");
        assertThat(features.get("indReportFiled")).isEqualTo("true");
        assertThat(features.get("susarOversight")).isEqualTo("true");
        assertThat(features.get("siteEnrollmentCount")).isEqualTo(45L);
        assertThat(features.get("siteTargetEnrollment")).isEqualTo(100);
        assertThat(features.get("agentTrustScore")).isEqualTo(0.82);
    }

    @Test
    void buildFeatures_nullTreatmentArm_returnsUNASSIGNED() {
        AdverseEvent ae = new AdverseEvent();
        ae.grade = CtcaeGrade.GRADE_1;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.susarOversightStatus = SusarOversightStatus.NONE;

        var ctx = new AeCbrContext(ae, new PatientEnrollment(), null, null, false, 0, 0, 0, 0.5);
        Map<String, Object> features = AeCbrFeatureBuilder.buildFeatures(ctx);

        assertThat(features.get("treatmentArm")).isEqualTo("UNASSIGNED");
    }

    @Test
    void buildFeatures_nullEnrollment_returnsUNASSIGNED() {
        AdverseEvent ae = new AdverseEvent();
        ae.grade = CtcaeGrade.GRADE_2;
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.susarOversightStatus = SusarOversightStatus.NONE;

        var ctx = new AeCbrContext(ae, null, null, null, false, 0, 0, 0, 0.5);
        Map<String, Object> features = AeCbrFeatureBuilder.buildFeatures(ctx);

        assertThat(features.get("treatmentArm")).isEqualTo("UNASSIGNED");
    }

    @Test
    void buildFeatures_nullGrade_returnsZero() {
        AdverseEvent ae = new AdverseEvent();
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.susarOversightStatus = SusarOversightStatus.NONE;

        var ctx = new AeCbrContext(ae, null, null, null, false, 0, 0, 0, 0.5);
        Map<String, Object> features = AeCbrFeatureBuilder.buildFeatures(ctx);

        assertThat(features.get("grade")).isEqualTo(0);
    }

    @Test
    void bucketPriorAeCount_zeroPrior_returnsNONE() {
        assertThat(AeCbrFeatureBuilder.bucketPriorAeCount(0)).isEqualTo("NONE");
    }

    @Test
    void bucketPriorAeCount_onePrior_returnsONE() {
        assertThat(AeCbrFeatureBuilder.bucketPriorAeCount(1)).isEqualTo("ONE");
    }

    @Test
    void bucketPriorAeCount_twoPlusPrior_returnsMULTIPLE() {
        assertThat(AeCbrFeatureBuilder.bucketPriorAeCount(2)).isEqualTo("MULTIPLE");
        assertThat(AeCbrFeatureBuilder.bucketPriorAeCount(10)).isEqualTo("MULTIPLE");
    }

    @Test
    void buildProblemSummary_formatsCorrectly() {
        AdverseEvent ae = new AdverseEvent();
        ae.grade = CtcaeGrade.GRADE_3;
        ae.eventType = "Neutropenia";
        ae.unexpected = true;
        ae.suspected = true;

        ClinicalTrial trial = new ClinicalTrial();
        trial.phase = TrialPhase.PHASE_III;

        var ctx = new AeCbrContext(ae, null, trial, null, false, 0, 0, 0, 0.5);
        String problem = AeCbrFeatureBuilder.buildProblemSummary(ctx);
        assertThat(problem).contains("Grade 3", "Neutropenia", "PHASE_III", "unexpected=true", "suspected=true");
    }

    @Test
    void buildSolutionSummary_formatsCorrectly() {
        AdverseEvent ae = new AdverseEvent();
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.FILED;
        ae.susarOversightStatus = SusarOversightStatus.COMPLETED;

        var ctx = new AeCbrContext(ae, null, null, "ESCALATE_TO_DSMB", true, 0, 0, 0, 0.5);
        String solution = AeCbrFeatureBuilder.buildSolutionSummary(ctx);
        assertThat(solution).contains("ESCALATE_TO_DSMB", "true");
    }

    @Test
    void buildProblemSummary_nullFields_handlesGracefully() {
        AdverseEvent ae = new AdverseEvent();

        var ctx = new AeCbrContext(ae, null, null, null, false, 0, 0, 0, 0.5);
        String problem = AeCbrFeatureBuilder.buildProblemSummary(ctx);
        assertThat(problem).contains("Grade 0", "UNKNOWN");
    }

    @Test
    void buildSolutionSummary_nullOutcome_handlesGracefully() {
        AdverseEvent ae = new AdverseEvent();
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.susarOversightStatus = SusarOversightStatus.NONE;

        var ctx = new AeCbrContext(ae, null, null, null, false, 0, 0, 0, 0.5);
        String solution = AeCbrFeatureBuilder.buildSolutionSummary(ctx);
        assertThat(solution).contains("UNKNOWN");
    }

    @Test
    void buildQueryFeatures_excludesAgentTrustScore() {
        var                 ctx      = new AeCbrContext(minimalAe(), null, null, null, false, 0, 10, 50, 0.75);
        Map<String, Object> features = AeCbrFeatureBuilder.buildQueryFeatures(ctx);

        assertThat(features).containsKey("siteEnrollmentCount");
        assertThat(features).containsKey("siteTargetEnrollment");
        assertThat(features).doesNotContainKey("agentTrustScore");
    }

    @Test
    void buildFeatures_trustScoreAtBounds() {
        var ctx0 = new AeCbrContext(minimalAe(), null, null, null, false, 0, 0, 0, 0.0);
        assertThat(AeCbrFeatureBuilder.buildFeatures(ctx0).get("agentTrustScore")).isEqualTo(0.0);

        var ctx1 = new AeCbrContext(minimalAe(), null, null, null, false, 0, 0, 0, 1.0);
        assertThat(AeCbrFeatureBuilder.buildFeatures(ctx1).get("agentTrustScore")).isEqualTo(1.0);
    }

    @Test
    void buildFeatures_zeroEnrollment() {
        var                 ctx      = new AeCbrContext(minimalAe(), null, null, null, false, 0, 0, 0, 0.5);
        Map<String, Object> features = AeCbrFeatureBuilder.buildFeatures(ctx);
        assertThat(features.get("siteEnrollmentCount")).isEqualTo(0L);
        assertThat(features.get("siteTargetEnrollment")).isEqualTo(0);
    }

    private AdverseEvent minimalAe() {
        AdverseEvent ae = new AdverseEvent();
        ae.regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;
        ae.susarOversightStatus       = SusarOversightStatus.NONE;
        return ae;
    }

}
