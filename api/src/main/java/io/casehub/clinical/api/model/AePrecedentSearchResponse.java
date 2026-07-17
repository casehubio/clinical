package io.casehub.clinical.api.model;

import java.util.List;

public record AePrecedentSearchResponse(
    String traceId,
    String explanation,
    List<AePrecedentResponse> precedents) {}
