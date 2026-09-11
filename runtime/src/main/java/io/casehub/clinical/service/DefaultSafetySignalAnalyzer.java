package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.SafetySignalAnalyzer;
import io.casehub.clinical.api.spi.SignalAnalysis;
import io.casehub.clinical.api.spi.TrialSafetyContext;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class DefaultSafetySignalAnalyzer implements SafetySignalAnalyzer {

    @Override
    public SignalAnalysis analyze(TrialSafetyContext context) {
        List<SignalAnalysis.AnalyzedSignal> signals = context.detectedSignals().stream()
                .map(s -> new SignalAnalysis.AnalyzedSignal(
                        s.signalType(), "UNKNOWN", s.affectedSiteIds(),
                        s.summary(), "Refer to DSMB for review"))
                .toList();
        return new SignalAnalysis(signals, "Rule-based signals passed through — no LLM analysis available", List.of());
    }
}
