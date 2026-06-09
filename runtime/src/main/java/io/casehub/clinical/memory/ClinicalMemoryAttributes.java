package io.casehub.clinical.memory;

public final class ClinicalMemoryAttributes {

    /** CTCAE grade of the adverse event — stored separately from OUTCOME so facts can carry both. */
    public static final String GRADE = "grade";

    private ClinicalMemoryAttributes() {}
}
