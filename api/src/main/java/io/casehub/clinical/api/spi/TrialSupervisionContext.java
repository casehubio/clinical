package io.casehub.clinical.api.spi;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TrialSupervisionContext(
        UUID trialId,
        String trialPhase,
        Map<String, Object> caseContext) {}
