package io.casehub.clinical.api;

/**
 * Canonical actor identity strings for the clinical harness in the tamper-evident ledger.
 * {@code CLINICAL_SERVICE} matches {@code ProtocolDeviationService.CLINICAL_SENDER} — both
 * identify the harness, in ledger and qhorus contexts respectively. Keep them in sync.
 */
public final class ClinicalActors {
    public static final String CLINICAL_SERVICE = "clinical-service";
    private ClinicalActors() {}
}
