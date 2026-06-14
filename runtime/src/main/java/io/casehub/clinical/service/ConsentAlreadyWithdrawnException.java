package io.casehub.clinical.service;

import java.util.UUID;

public class ConsentAlreadyWithdrawnException extends RuntimeException {
    public ConsentAlreadyWithdrawnException(UUID enrollmentId) {
        super("Consent already withdrawn for enrollment " + enrollmentId);
    }
}
