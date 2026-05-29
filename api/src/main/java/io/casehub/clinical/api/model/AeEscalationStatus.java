package io.casehub.clinical.api.model;

public enum AeEscalationStatus {
    /** Default — Grade 1/2 AEs; no escalation initiated. */
    NONE,
    /** Grade 3+; escalation case started. */
    REQUESTED,
    /** Engine case reached CaseCompleted with safety review. */
    COMPLETED,
    /** Case start failed (engine unavailable, pool timeout). */
    FAILED
}
