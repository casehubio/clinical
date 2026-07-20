package io.casehub.clinical.api.model;

import io.casehub.api.spi.routing.CandidateSetStrategy;
import io.casehub.api.spi.routing.StaticSetStrategy;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Typed taxonomy of consequential clinical trial agent actions requiring oversight gates.
 * All types are ALWAYS-gated — these are regulatory obligations, not configurable policy.
 *
 * <p>candidateGroups semantics (GE-20260607-326c7e): fewer entries = more restrictive
 * in {@code ChainedReactiveActionRiskClassifier.narrower()}. SUSAR types (1 group) are the
 * tightest gates. Protocol deviation recording (2 groups) is broadest.
 *
 * <p>Classification logic lives in {@code ClinicalActionRiskClassifier}. This enum owns
 * only the data — pure Java, no framework dependencies.
 */
public enum ClinicalActionType {

    SUSAR_CRITERIA_DECISION(
        false,
        StaticSetStrategy.of("qualified-investigator"),
        "SUSAR criteria met — clinician sign-off required before regulatory clock starts"),

    SUSAR_REGULATORY_FILING(
        false,
        StaticSetStrategy.of("qualified-investigator"),
        "Regulatory submission of SUSAR report — qualified investigator confirmation required"),

    PATIENT_WITHDRAWAL(
        false,
        StaticSetStrategy.of("principal-investigator"),
        "Patient withdrawal is irreversible — principal investigator confirmation required"),

    DOSE_MODIFICATION(
        true,
        StaticSetStrategy.of("principal-investigator"),
        "Dose modification recommendation requires physician approval — reversible"),

    PROTOCOL_DEVIATION_RECORDING(
        false,
        StaticSetStrategy.of("principal-investigator", "irb-committee"),
        "Protocol deviation recording — PI or IRB committee confirmation required");

    private static final String OVERSIGHT_SCOPE = "casehubio/clinical/oversight";

    private final boolean reversible;
    private final CandidateSetStrategy candidateGroups;
    private final String reason;

    ClinicalActionType(
            final boolean reversible,
            final CandidateSetStrategy candidateGroups,
            final String reason) {
        this.reversible = reversible;
        this.candidateGroups = candidateGroups;
        this.reason = reason;
    }

    public boolean reversible()                    { return reversible; }
    public CandidateSetStrategy candidateGroups() { return candidateGroups; }
    public String reason()                { return reason; }
    public String scope()                 { return OVERSIGHT_SCOPE; }

    /**
     * Null — regulatory deadline policy is post-GA deployment config, not compile-time constant.
     */
    public Duration expiresIn() {return null;}

    /**
     * Null — typed gate resolutions not yet defined for clinical action types.
     */
    public Class<?> resolutionType() {return null;}

    /** Returns the PlannedAction actionType string: {@code SUSAR_CRITERIA_DECISION → "susar.criteria.decision"}. */
    public String actionType() {
        return name().toLowerCase().replace('_', '.');
    }

    /** Parses a {@code PlannedAction.actionType()} string back to the enum constant. Null-safe. */
    public static Optional<ClinicalActionType> fromActionType(final String actionType) {
        if (actionType == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(a -> a.actionType().equals(actionType))
                .findFirst();
    }
}
