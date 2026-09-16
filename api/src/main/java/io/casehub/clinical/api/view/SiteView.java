package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.SiteStatus;

import java.util.UUID;

public record SiteView(UUID id, UUID trialId, String investigatorId,
                        int targetEnrollment, SiteStatus status) {}
