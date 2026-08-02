package io.casehub.clinical.api.model;

public enum ClinicalScope {
    TRIAL(1),
    SITE(2),
    PATIENT(3);

    private final int depth;

    ClinicalScope(int depth) {
        this.depth = depth;
    }

    public int depth() {
        return depth;
    }
}
