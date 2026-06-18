package io.casehub.clinical.api.model;

/** Engine case lifecycle status — same shape as SusarOversightStatus / AeEscalationStatus. */
public enum EligibilityScreeningCaseStatus {
    /** No eligibility screening case started — initial state. */
    NONE,
    /** Eligibility screening case start requested; engine case being initialised. */
    REQUESTED,
    /** Engine case goal reached — IRB consultation completed or criteria assessed. */
    COMPLETED,
    /** Engine case start failed; eligibility screening case status stuck. */
    FAILED
}
