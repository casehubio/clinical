package io.casehub.clinical.service;

/**
 * No active (non-withdrawn) enrollments found for this patientId.
 *
 * <p>After a successful GDPR erasure, the original patientId is pseudonymized.
 * Retries with the original ID will receive this exception. This is by design:
 * erased patients are unidentifiable.
 */
public class PatientNotFoundException extends RuntimeException {
    private final String patientId;

    public PatientNotFoundException(String patientId) {
        super("No active enrollments found for patient: " + patientId);
        this.patientId = patientId;
    }

    public String patientId() { return patientId; }
}
