package io.casehub.clinical.api.spi;

import java.util.List;

public record SignalAnalysis(
        List<AnalyzedSignal> signals,
        String overallAssessment,
        List<String> additionalPatterns) {

    public record AnalyzedSignal(String signalType, String severity,
                                  List<String> affectedSites, String narrative,
                                  String recommendedAction) {}
}
