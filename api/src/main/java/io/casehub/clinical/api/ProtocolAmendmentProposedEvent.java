package io.casehub.clinical.api;

import java.util.UUID;

public record ProtocolAmendmentProposedEvent(
    UUID amendmentId,
    UUID trialId,
    String proposedChange,
    String tenantId
) {}
