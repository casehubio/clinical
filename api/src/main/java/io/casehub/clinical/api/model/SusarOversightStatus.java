package io.casehub.clinical.api.model;

public enum SusarOversightStatus {
    /** Default — Grade 1/2 or criteria not met; no oversight case. */
    NONE,
    /** SUSAR criteria confirmed; oversight case start requested. */
    REQUESTED,
    /** Oversight case goal satisfied (gate approved or rejected). */
    COMPLETED,
    /** Case start failed — engine unavailable or pool timeout. */
    FAILED
}
