package io.casehub.clinical.agent;

public record SusarAssessmentResponse(
        boolean susarRequired,
        String causalityAssessment,
        String expectednessAssessment,
        String reasoning,
        float confidence) {}
