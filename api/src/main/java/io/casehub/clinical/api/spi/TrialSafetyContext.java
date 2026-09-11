package io.casehub.clinical.api.spi;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TrialSafetyContext(
        UUID trialId,
        String trialPhase,
        Map<UUID, List<SiteAeSummary>> siteData,
        List<DetectedSignalSummary> detectedSignals) {

    public record SiteAeSummary(String grade, String eventType, int count) {}
    public record DetectedSignalSummary(String signalType, List<String> affectedSiteIds,
                                         String summary, String dominantGrade, String dominantEventType) {}
}
