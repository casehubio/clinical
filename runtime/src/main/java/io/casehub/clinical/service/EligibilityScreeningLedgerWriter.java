package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.model.EligibilityScreeningResult;
import io.casehub.clinical.entity.PatientEnrollment;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Stub — full implementation in Task 4 (EligibilityScreeningLedgerEntry + LedgerWriter).
 */
@ApplicationScoped
public class EligibilityScreeningLedgerWriter {

    public void writeScreeningEntry(PatientEnrollment enrollment,
                                    List<CriterionResult> criteria,
                                    EligibilityScreeningResult result) {
        // Task 4 implementation
    }
}
