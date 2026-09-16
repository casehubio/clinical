package io.casehub.clinical.api.view;

import java.util.List;

public record SiteEnrollmentTrajectoryView(List<EnrollmentObservationView> observations,
                                            TrajectoryTrendSummaryView trends) {}
