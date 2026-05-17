package io.casehub.clinical.api.model;

public enum PiApprovalStatus {
    PENDING,     // reported; COMMAND not yet issued (transient in service)
    COMMANDED,   // COMMAND issued, Commitment OPEN, awaiting PI
    APPROVED,    // PI approved; MINOR deviations close here
    REJECTED,    // PI declined
    EXPIRED,     // deadline passed without response — GCP SLA breach
    ESCALATED    // PI approved; forwarded to IRB (CRITICAL) or sponsor (MAJOR)
}
