package io.casehub.clinical.api.view;

public record TrajectoryObservationView(long secondsSinceReport, int escalation, int susar, int regulatory) {}
