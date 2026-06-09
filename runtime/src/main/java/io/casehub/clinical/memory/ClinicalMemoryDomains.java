package io.casehub.clinical.memory;

import io.casehub.platform.api.memory.MemoryDomain;

public final class ClinicalMemoryDomains {

    public static final MemoryDomain PATIENT = new MemoryDomain("clinical-patient");
    public static final MemoryDomain SITE    = new MemoryDomain("clinical-site");

    /**
     * Cross-site AE signal aggregation per trial.
     * entityId convention: {@code trial:{trialId}}.
     * Tenant-scoped — no cross-tenant signal sharing.
     */
    public static final MemoryDomain DRUG = new MemoryDomain("clinical-drug");

    private ClinicalMemoryDomains() {}
}
