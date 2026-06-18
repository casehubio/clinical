package io.casehub.clinical.api.model;

public enum ProtocolAmendmentStatus {
    PROPOSED,
    /** Terminal: advisor recommended DSMB review (pending clinical#86 / engine#101). */
    SUPERVISED,
    APPROVED,
    HALTED
}
