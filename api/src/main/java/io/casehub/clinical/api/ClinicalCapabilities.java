package io.casehub.clinical.api;

/** Capability tags used in CasePlanModel bindings and agent registration. */
public final class ClinicalCapabilities {
    private ClinicalCapabilities() {}

    public static final String ELIGIBILITY_SCREENING  = "eligibility-screening";
    public static final String SAFETY_MONITORING      = "safety-monitoring";
    public static final String PROTOCOL_REVIEW        = "protocol-review";
    public static final String IRB_CONSULTATION       = "irb-consultation";
    public static final String PI_AUTHORISATION       = "pi-authorisation";
    public static final String DATA_SAFETY_MONITORING = "data-safety-monitoring";
    public static final String REGULATORY_SUBMISSION  = "regulatory-submission";
    public static final String TRIAL_SUPERVISOR       = "trial-supervisor";
}
