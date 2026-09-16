package io.casehub.clinical.api.view;

import java.util.List;

public record TrajectoryMatchView(String caseId, double score, String outcome,
                                   List<TrajectoryObservationView> trajectoryObs) {}
