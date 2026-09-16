package io.casehub.clinical.api.view;

import java.util.UUID;

public record DashboardPatientView(
        UUID id, UUID siteId, String patientId, String enrollmentStatus,
        String screeningResult, String consentStatus
) {}
