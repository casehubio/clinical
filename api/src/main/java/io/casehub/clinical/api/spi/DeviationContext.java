package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.TrialPhase;
import java.util.UUID;

public record DeviationContext(
    UUID deviationId,
    UUID siteId,
    UUID trialId,
    String protocolId,
    TrialPhase phase,
    DeviationSeverity severity,
    String deviationType
) {}
