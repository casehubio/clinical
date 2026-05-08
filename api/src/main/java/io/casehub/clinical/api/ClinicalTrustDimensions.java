package io.casehub.clinical.api;

/** Trust dimension keys for agent trust scoring via casehub-ledger. */
public final class ClinicalTrustDimensions {
    private ClinicalTrustDimensions() {}

    public static final String SAFETY_ACCURACY        = "safety-accuracy";
    public static final String ELIGIBILITY_PRECISION  = "eligibility-precision";
    public static final String PROTOCOL_ADHERENCE     = "protocol-adherence";
}
