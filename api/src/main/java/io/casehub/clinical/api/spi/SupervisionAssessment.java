package io.casehub.clinical.api.spi;

import java.util.List;

public record SupervisionAssessment(
        String overallHealth,
        List<SupervisionFinding> findings,
        String summary) {

    public record SupervisionFinding(String findingType, List<String> affectedSites,
                                      String severity, String narrative,
                                      String recommendedAction) {}
}
