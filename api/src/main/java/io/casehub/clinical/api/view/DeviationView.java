package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;

import java.time.Instant;
import java.util.UUID;

public record DeviationView(UUID id, UUID siteId, String deviationType,
                              DeviationSeverity severity, PiApprovalStatus piApprovalStatus,
                              Instant commandedAt, Instant responseDeadline,
                              EscalationRequirement escalationRequirement) {}
