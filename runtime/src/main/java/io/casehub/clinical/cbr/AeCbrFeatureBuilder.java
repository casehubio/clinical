package io.casehub.clinical.cbr;

import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.PatientEnrollment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AeCbrFeatureBuilder {

    private AeCbrFeatureBuilder() {}

    public static Map<String, Object> buildFeatures(AeCbrContext ctx) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("grade", ctx.ae().grade != null ? ctx.ae().grade.ordinal() + 1 : 0);
        features.put("eventType", List.of(ctx.ae().eventType != null ? ctx.ae().eventType : "UNKNOWN"));
        features.put("trialPhase", ctx.trial() != null && ctx.trial().phase != null ? ctx.trial().phase.name() : "UNKNOWN");
        features.put("unexpected", String.valueOf(ctx.ae().unexpected));
        features.put("suspected", String.valueOf(ctx.ae().suspected));
        features.put("treatmentArm", ctx.enrollment() != null && ctx.enrollment().treatmentArm != null
                                     ? ctx.enrollment().treatmentArm : "UNASSIGNED");
        features.put("priorAeCount", bucketPriorAeCount(ctx.priorAeCount()));
        features.put("safetyReviewOutcome", ctx.safetyReviewOutcome() != null ? ctx.safetyReviewOutcome() : "UNKNOWN");
        features.put("dsmbEscalated", String.valueOf(ctx.dsmbEscalated()));
        features.put("indReportFiled", String.valueOf(ctx.ae().regulatorySubmissionStatus != RegulatorySubmissionStatus.NONE));
        features.put("susarOversight", String.valueOf(ctx.ae().susarOversightStatus != SusarOversightStatus.NONE));
        features.put("siteEnrollmentCount", ctx.siteEnrollmentCount());
        features.put("siteTargetEnrollment", ctx.siteTargetEnrollment());
        features.put("agentTrustScore", ctx.agentTrustScore());
        return features;
    }

    public static Map<String, Object> buildQueryFeatures(AeCbrContext ctx) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("grade", ctx.ae().grade != null ? ctx.ae().grade.ordinal() + 1 : 0);
        features.put("eventType", List.of(ctx.ae().eventType != null ? ctx.ae().eventType : "UNKNOWN"));
        features.put("trialPhase", ctx.trial() != null && ctx.trial().phase != null ? ctx.trial().phase.name() : "UNKNOWN");
        features.put("unexpected", String.valueOf(ctx.ae().unexpected));
        features.put("suspected", String.valueOf(ctx.ae().suspected));
        features.put("treatmentArm", ctx.enrollment() != null && ctx.enrollment().treatmentArm != null
                                     ? ctx.enrollment().treatmentArm : "UNASSIGNED");
        features.put("priorAeCount", bucketPriorAeCount(ctx.priorAeCount()));
        features.put("siteEnrollmentCount", ctx.siteEnrollmentCount());
        features.put("siteTargetEnrollment", ctx.siteTargetEnrollment());
        return features;
    }

    public static String bucketPriorAeCount(long count) {
        if (count <= 0) return "NONE";
        if (count == 1) return "ONE";
        return "MULTIPLE";
    }

    public static String buildProblemSummary(AeCbrContext ctx) {
        return "Grade %d %s in %s trial, unexpected=%s, suspected=%s".formatted(
                ctx.ae().grade != null ? ctx.ae().grade.ordinal() + 1 : 0,
                ctx.ae().eventType != null ? ctx.ae().eventType : "UNKNOWN",
                ctx.trial() != null && ctx.trial().phase != null ? ctx.trial().phase.name() : "UNKNOWN",
                ctx.ae().unexpected, ctx.ae().suspected);
    }

    public static String buildSolutionSummary(AeCbrContext ctx) {
        return "Safety review: %s. DSMB escalated: %s. IND report: %s. SUSAR oversight: %s.".formatted(
                ctx.safetyReviewOutcome() != null ? ctx.safetyReviewOutcome() : "UNKNOWN",
                ctx.dsmbEscalated(),
                ctx.ae().regulatorySubmissionStatus != RegulatorySubmissionStatus.NONE,
                ctx.ae().susarOversightStatus != SusarOversightStatus.NONE);
    }


}
