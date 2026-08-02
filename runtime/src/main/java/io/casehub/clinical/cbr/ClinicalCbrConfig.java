package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class ClinicalCbrConfig {

    @ConfigProperty(name = "casehub.clinical.cbr.scope-decay.ae", defaultValue = "exponential:0.7")
    String aeScopeDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.scope-decay.deviation", defaultValue = "exponential:0.8")
    String deviationScopeDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.scope-decay.amendment", defaultValue = "step:1.0")
    String amendmentScopeDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.scope-decay.site-enrollment", defaultValue = "exponential:0.7")
    String siteEnrollmentScopeDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.scope-decay.trial-safety", defaultValue = "step:1.0")
    String trialSafetyScopeDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.temporal-decay.ae", defaultValue = "halflife:90d")
    String aeTemporalDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.temporal-decay.ae-trajectory", defaultValue = "halflife:60d")
    String aeTrajectoryTemporalDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.temporal-decay.deviation", defaultValue = "halflife:180d")
    String deviationTemporalDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.temporal-decay.amendment", defaultValue = "halflife:365d")
    String amendmentTemporalDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.temporal-decay.site-enrollment", defaultValue = "halflife:60d")
    String siteEnrollmentTemporalDecayStr;

    @ConfigProperty(name = "casehub.clinical.cbr.temporal-decay.trial-safety", defaultValue = "halflife:90d")
    String trialSafetyTemporalDecayStr;

    public ScopeDecay aeScopeDecay() { return parseScopeDecay(aeScopeDecayStr); }
    public ScopeDecay deviationScopeDecay() { return parseScopeDecay(deviationScopeDecayStr); }
    public ScopeDecay amendmentScopeDecay() { return parseScopeDecay(amendmentScopeDecayStr); }
    public ScopeDecay siteEnrollmentScopeDecay() { return parseScopeDecay(siteEnrollmentScopeDecayStr); }
    public ScopeDecay trialSafetyScopeDecay() { return parseScopeDecay(trialSafetyScopeDecayStr); }

    public TemporalDecay aeTemporalDecay() { return parseTemporalDecay(aeTemporalDecayStr); }
    public TemporalDecay aeTrajectoryTemporalDecay() { return parseTemporalDecay(aeTrajectoryTemporalDecayStr); }
    public TemporalDecay deviationTemporalDecay() { return parseTemporalDecay(deviationTemporalDecayStr); }
    public TemporalDecay amendmentTemporalDecay() { return parseTemporalDecay(amendmentTemporalDecayStr); }
    public TemporalDecay siteEnrollmentTemporalDecay() { return parseTemporalDecay(siteEnrollmentTemporalDecayStr); }
    public TemporalDecay trialSafetyTemporalDecay() { return parseTemporalDecay(trialSafetyTemporalDecayStr); }

    static ScopeDecay parseScopeDecay(String value) {
        if (value == null) return null;
        String[] parts = value.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("Invalid scope decay format: " + value);
        return switch (parts[0]) {
            case "exponential" -> new ScopeDecay.Exponential(Double.parseDouble(parts[1]));
            case "linear" -> new ScopeDecay.Linear(Integer.parseInt(parts[1]));
            case "step" -> new ScopeDecay.Step(Double.parseDouble(parts[1]));
            default -> throw new IllegalArgumentException("Unknown scope decay type: " + parts[0]);
        };
    }

    static TemporalDecay parseTemporalDecay(String value) {
        if (value == null) return null;
        String[] parts = value.split(":");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid temporal decay format: " + value);
        return switch (parts[0]) {
            case "halflife" -> new TemporalDecay.HalfLife(parseDuration(parts[1]));
            case "linear" -> new TemporalDecay.Linear(parseDuration(parts[1]));
            case "step" -> {
                if (parts.length != 3) throw new IllegalArgumentException("Step temporal decay requires cutoff:afterCutoff: " + value);
                yield new TemporalDecay.Step(parseDuration(parts[1]), Double.parseDouble(parts[2]));
            }
            default -> throw new IllegalArgumentException("Unknown temporal decay type: " + parts[0]);
        };
    }

    private static Duration parseDuration(String s) {
        if (s.endsWith("d")) return Duration.ofDays(Long.parseLong(s.substring(0, s.length() - 1)));
        if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
        return Duration.parse("PT" + s);
    }
}
