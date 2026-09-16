package io.casehub.clinical.api.view;

import java.util.List;

public record AeTrajectoryMatchView(List<TrajectoryMatchView> matches, String traceId, String explanation) {}
