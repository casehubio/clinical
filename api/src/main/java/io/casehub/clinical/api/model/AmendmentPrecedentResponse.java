package io.casehub.clinical.api.model;

public record AmendmentPrecedentResponse(
    double score,
    String proposedChange,
    String advisorOutcome,
    String outcome
) {}
