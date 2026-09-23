package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.CbrRecord;

import java.util.List;

public record AuditedRetrievalResult<C extends CbrRecord>(
    List<CbrMatch<C>> cases,
    String traceId,
    String explanation) {}
