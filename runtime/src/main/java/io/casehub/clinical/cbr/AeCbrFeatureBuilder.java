package io.casehub.clinical.cbr;

import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AeCbrFeatureBuilder {

    private AeCbrFeatureBuilder() {}

    public static Map<String, Object> buildFeatures(AdverseEvent ae,
                                                     PatientEnrollment enrollment,
                                                     ClinicalTrial trial,
                                                     String safetyReviewOutcome,
                                                     boolean dsmbEscalated,
                                                     long priorAeCount) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("grade", ae.grade != null ? ae.grade.ordinal() + 1 : 0);
        features.put("eventType", ae.eventType != null ? ae.eventType : "UNKNOWN");
        features.put("trialPhase", trial != null && trial.phase != null ? trial.phase.name() : "UNKNOWN");
        features.put("unexpected", String.valueOf(ae.unexpected));
        features.put("suspected", String.valueOf(ae.suspected));
        features.put("treatmentArm", enrollment != null && enrollment.treatmentArm != null
            ? enrollment.treatmentArm : "UNASSIGNED");
        features.put("priorAeCount", bucketPriorAeCount(priorAeCount));
        features.put("safetyReviewOutcome", safetyReviewOutcome != null ? safetyReviewOutcome : "UNKNOWN");
        features.put("dsmbEscalated", String.valueOf(dsmbEscalated));
        features.put("indReportFiled", String.valueOf(ae.regulatorySubmissionStatus != RegulatorySubmissionStatus.NONE));
        features.put("susarOversight", String.valueOf(ae.susarOversightStatus != SusarOversightStatus.NONE));
        return features;
    }

    public static Map<String, Object> buildQueryFeatures(AdverseEvent ae,
                                                          PatientEnrollment enrollment,
                                                          ClinicalTrial trial,
                                                          long priorAeCount) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("grade", ae.grade != null ? ae.grade.ordinal() + 1 : 0);
        features.put("eventType", ae.eventType != null ? ae.eventType : "UNKNOWN");
        features.put("trialPhase", trial != null && trial.phase != null ? trial.phase.name() : "UNKNOWN");
        features.put("unexpected", String.valueOf(ae.unexpected));
        features.put("suspected", String.valueOf(ae.suspected));
        features.put("treatmentArm", enrollment != null && enrollment.treatmentArm != null
            ? enrollment.treatmentArm : "UNASSIGNED");
        features.put("priorAeCount", bucketPriorAeCount(priorAeCount));
        return features;
    }

    public static String bucketPriorAeCount(long count) {
        if (count <= 0) return "NONE";
        if (count == 1) return "ONE";
        return "MULTIPLE";
    }

    public static String buildProblemSummary(AdverseEvent ae, ClinicalTrial trial) {
        return "Grade %d %s in %s trial, unexpected=%s, suspected=%s".formatted(
            ae.grade != null ? ae.grade.ordinal() + 1 : 0,
            ae.eventType != null ? ae.eventType : "UNKNOWN",
            trial != null && trial.phase != null ? trial.phase.name() : "UNKNOWN",
            ae.unexpected, ae.suspected);
    }

    public static String buildSolutionSummary(String safetyReviewOutcome,
                                               boolean dsmbEscalated,
                                               AdverseEvent ae) {
        return "Safety review: %s. DSMB escalated: %s. IND report: %s. SUSAR oversight: %s.".formatted(
            safetyReviewOutcome != null ? safetyReviewOutcome : "UNKNOWN",
            dsmbEscalated,
            ae.regulatorySubmissionStatus != RegulatorySubmissionStatus.NONE,
            ae.susarOversightStatus != SusarOversightStatus.NONE);
    }
}
