package io.casehub.clinical.api.model;

import java.util.List;

public record DeviationPrecedentSearchResponse(
    String traceId,
    String explanation,
    List<DeviationPrecedentResponse> precedents) {}
