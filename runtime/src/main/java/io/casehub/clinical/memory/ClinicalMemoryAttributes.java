package io.casehub.clinical.memory;

public final class ClinicalMemoryAttributes {

    /** CTCAE grade of the adverse event — stored separately from OUTCOME so facts can carry both. */
    public static final String GRADE = "grade";

    /** Site UUID as string — carried on DRUG domain entries for cross-site signal attribution. */
    public static final String SITE_ID = "site-id";

    private ClinicalMemoryAttributes() {}
}
