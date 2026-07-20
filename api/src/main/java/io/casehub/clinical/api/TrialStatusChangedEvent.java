package io.casehub.clinical.api;

import io.casehub.clinical.api.model.TrialStatus;
import java.util.UUID;

public record TrialStatusChangedEvent(
    UUID trialId, TrialStatus oldStatus, TrialStatus newStatus,
    String tenantId) {}
