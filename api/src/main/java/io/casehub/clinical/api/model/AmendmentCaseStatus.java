package io.casehub.clinical.api.model;

/** Engine case lifecycle status — same shape as AeEscalationStatus / SusarOversightStatus. */
public enum AmendmentCaseStatus {
    /** No amendment engine case started — initial state. */
    NONE,
    /** Amendment engine case start requested; advisor being invoked. */
    REQUESTED,
    /** Engine case goal reached — advisor recommendation applied. */
    COMPLETED,
    /** Engine case start failed; amendment stays at initial business status. */
    FAILED
}
