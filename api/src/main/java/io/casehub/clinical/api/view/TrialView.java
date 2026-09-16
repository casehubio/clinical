package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;

import java.util.UUID;

public record TrialView(UUID id, String protocolId, TrialPhase phase, String sponsor,
                         int targetEnrollment, TrialStatus status,
                         String sponsorNotificationConnectorId,
                         String sponsorNotificationDestination) {}
