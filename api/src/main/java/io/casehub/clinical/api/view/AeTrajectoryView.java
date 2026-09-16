package io.casehub.clinical.api.view;

import java.util.List;
import java.util.UUID;

public record AeTrajectoryView(UUID aeId, List<TrajectoryObservationView> observations,
                                TrajectoryTrendSummaryView trends) {}
