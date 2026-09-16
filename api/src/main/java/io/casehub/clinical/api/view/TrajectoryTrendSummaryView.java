package io.casehub.clinical.api.view;

import java.util.Map;

public record TrajectoryTrendSummaryView(Map<String, DimensionTrendView> dimensions) {}
