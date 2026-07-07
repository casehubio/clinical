package io.casehub.clinical.api.model;

public record AePrecedentResponse(
    double score,
    String grade,
    String eventType,
    String trialPhase,
    boolean unexpected,
    boolean suspected,
    String safetyReviewOutcome,
    boolean dsmbEscalated,
    boolean indReportFiled,
    boolean susarOversight,
    String problem,
    String outcome
) {}
