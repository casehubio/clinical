package io.casehub.clinical.api.model;

public enum EscalationRequirement {
    NONE,                  // site-level resolution only (MINOR)
    SPONSOR_NOTIFICATION,  // notify trial sponsor (MAJOR) — casehubio/clinical#13
    IRB_REVIEW             // ethics committee gate (CRITICAL) — casehubio/clinical#6
}
