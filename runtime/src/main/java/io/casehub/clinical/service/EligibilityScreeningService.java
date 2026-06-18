package io.casehub.clinical.service;

import io.casehub.clinical.api.EligibilityScreeningEvent;
import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.model.EligibilityScreeningResult;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class EligibilityScreeningService {

    @Inject EligibilityScreeningLedgerWriter ledgerWriter;
    @Inject Event<EligibilityScreeningEvent> screeningEvents;

    @Transactional
    public void screen(PatientEnrollment enrollment, List<CriterionResult> criteria) {
        EligibilityScreeningResult result = determineResult(criteria);
        enrollment.screeningResult = result;
        enrollment.screeningCompletedAt = Instant.now();
        enrollment.enrollmentStatus = switch (result) {
            case CRITERIA_MET -> EnrollmentStatus.ELIGIBLE;
            case EXCLUDED     -> EnrollmentStatus.INELIGIBLE;
            case MARGINAL     -> EnrollmentStatus.SCREENING;
        };
        ledgerWriter.writeScreeningEntry(enrollment, criteria, result);
        if (result == EligibilityScreeningResult.MARGINAL) {
            screeningEvents.fireAsync(new EligibilityScreeningEvent(
                enrollment.id, enrollment.tenantId, result, criteria));
        }
    }

    /** Package-private for unit testing. */
    EligibilityScreeningResult determineResult(List<CriterionResult> criteria) {
        boolean anyMarginal = criteria.stream().anyMatch(CriterionResult::marginal);
        if (anyMarginal) return EligibilityScreeningResult.MARGINAL;
        boolean anyExcluded = criteria.stream().anyMatch(c -> !c.met());
        if (anyExcluded) return EligibilityScreeningResult.EXCLUDED;
        return EligibilityScreeningResult.CRITERIA_MET;
    }
}
