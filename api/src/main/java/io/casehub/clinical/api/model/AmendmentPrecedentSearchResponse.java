package io.casehub.clinical.api.model;

import java.util.List;

public record AmendmentPrecedentSearchResponse(
    String traceId,
    String explanation,
    List<AmendmentPrecedentResponse> precedents) {}
