package io.casehub.clinical.api.view;

import java.util.UUID;

public record DashboardSiteView(UUID id, String siteName, String investigatorId, String status,
                                long enrolledCount, long adverseEventCount, long deviationCount,
                                int targetEnrollment) {}
