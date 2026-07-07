package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.MemoryDomain;

/**
 * CBR domain constants for clinical trial case-based reasoning.
 * <p>
 * Each domain represents a class of clinical coordination problems where
 * past precedent informs future decisions:
 * <ul>
 * <li>AE — adverse event safety escalation decisions</li>
 * <li>DEVIATION — protocol deviation PI/IRB approval patterns</li>
 * <li>AMENDMENT — protocol amendment advisor recommendations</li>
 * </ul>
 */
public final class ClinicalCbrDomains {

    public static final MemoryDomain AE = new MemoryDomain("clinical-ae");
    public static final MemoryDomain DEVIATION = new MemoryDomain("clinical-deviation");
    public static final MemoryDomain AMENDMENT = new MemoryDomain("clinical-amendment");

    private ClinicalCbrDomains() {}
}
