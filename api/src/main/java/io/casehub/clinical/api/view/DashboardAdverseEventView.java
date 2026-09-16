package io.casehub.clinical.api.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardAdverseEventView(
        UUID id, UUID enrollmentId, UUID siteId, String siteName,
        String patientId, String grade, String eventType,
        Instant reportedAt, Instant slaDeadline, String escalationStatus,
        String regulatorySubmissionStatus, String slaTimeRemaining,
        Double slaTimeRemainingHours,
        List<DashboardGradeChangeView> gradeHistory
) {}
