package io.casehub.clinical.report.model;

import io.casehub.clinical.api.model.TrialPhase;
import java.util.UUID;

public record TrialReportContext(
        UUID trialId,
        String protocolId,
        TrialPhase phase,
        String sponsor,
        int totalSites,
        int totalEnrolled) {}
