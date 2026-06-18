package io.casehub.clinical.api.spi;

import java.util.Map;
import java.util.UUID;

public record ProtocolAmendmentContext(
    UUID amendmentId,
    UUID trialId,
    String proposedChange,
    Map<String, Object> trialBlackboardSnapshot
) {}
