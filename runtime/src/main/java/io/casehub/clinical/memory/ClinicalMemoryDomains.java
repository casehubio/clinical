package io.casehub.clinical.memory;

import io.casehub.neocortex.memory.MemoryDomain;

public final class ClinicalMemoryDomains {

    public static final MemoryDomain PATIENT = new MemoryDomain("clinical-patient");
    public static final MemoryDomain SITE    = new MemoryDomain("clinical-site");

    /**
     * Cross-site AE signal aggregation per trial.
     * entityId convention: {@code trial:{trialId}}.
     * Tenant-scoped — no cross-tenant signal sharing.
     */
    public static final MemoryDomain DRUG = new MemoryDomain("clinical-drug");

    /**
     * IRB decision precedent per deviation type.
     * entityId convention: {@code deviation-type:{deviationType}}.
     * Aggregates all IRB decisions for a given deviation type across sites and trials.
     * Enables the IRB consultation agent to query "how has this type been decided before?"
     */
    public static final MemoryDomain IRB = new MemoryDomain("clinical-irb");

    private ClinicalMemoryDomains() {}
}
