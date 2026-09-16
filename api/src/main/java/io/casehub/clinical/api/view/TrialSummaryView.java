package io.casehub.clinical.api.view;

public record TrialSummaryView(
        String protocolId, String phase, String sponsor, int targetEnrollment,
        long totalEnrolled, long totalAdverseEvents, long totalDeviations
) {}
