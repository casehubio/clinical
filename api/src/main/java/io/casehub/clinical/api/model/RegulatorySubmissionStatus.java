package io.casehub.clinical.api.model;

public enum RegulatorySubmissionStatus {
    /** Default — AE is not Grade 5 + unexpected; no IND submission triggered. */
    NONE,
    /** Grade 5 + unexpected confirmed; regulatory submission case started. */
    PENDING,
    /** IND expedited safety report filed. */
    FILED
}
