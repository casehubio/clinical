package io.casehub.clinical.api.model;

import java.util.List;

public record AePrecedentResponse(
        double score,
        String grade,
        String eventType,
        String trialPhase,
        boolean unexpected,
        boolean suspected,
        String treatmentArm,
        String priorAeCount,
        String safetyReviewOutcome,
        boolean dsmbEscalated,
        boolean indReportFiled,
        boolean susarOversight,
        List<PlanStepResponse> steps,
        String problem,
        String outcome
) {}
