package io.casehub.clinical.service;

import java.util.UUID;

public class PatientEnrollmentNotFoundException extends RuntimeException {
    public PatientEnrollmentNotFoundException(UUID enrollmentId) {
        super("Enrollment not found: " + enrollmentId);
    }
}
