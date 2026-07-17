package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;

import java.util.List;

public record AuditedRetrievalResult<C extends CbrCase>(
    List<ScoredCbrCase<C>> cases,
    String traceId,
    String explanation) {}
