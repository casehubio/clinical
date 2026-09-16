package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.TrialPhase;

public record RegisterTrialRequest(String protocolId, TrialPhase phase, String sponsor,
                                    int targetEnrollment,
                                    String sponsorNotificationConnectorId,
                                    String sponsorNotificationDestination) {}
