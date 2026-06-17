package io.casehub.clinical.api.model;

public enum RegulatorySubmissionStatus {
    /** Default — AE does not meet IND reportable criteria; no submission triggered. */
    NONE,
    /** Grade 3/4/5 + unexpected confirmed; regulatory submission case started. */
    PENDING,
    /** IND expedited safety report filed. */
    FILED
}
