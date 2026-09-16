package io.casehub.clinical.api.view;

import java.time.Instant;
import java.util.UUID;

public record DashboardDeviationView(
        UUID id, UUID siteId, String siteName, String deviationType,
        String severity, String piApprovalStatus,
        Instant reportedAt, String irbDecision
) {}
