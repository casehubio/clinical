package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.spi.TrialSafetyContext;

public interface SafetySignalAnalyzer {
    SignalAnalysis analyze(TrialSafetyContext context);
}
