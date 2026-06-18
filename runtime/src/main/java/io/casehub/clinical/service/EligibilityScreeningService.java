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
        // eligibilityScreeningCaseStatus stays NONE for CRITERIA_MET and EXCLUDED —
        // no engine case is started for those paths. Only MARGINAL triggers a case
        // (via EligibilityScreeningCaseService observing the CDI event below).
        ledgerWriter.writeScreeningEntry(enrollment, criteria, result);
        if (result == EligibilityScreeningResult.MARGINAL) {
            screeningEvents.fireAsync(new EligibilityScreeningEvent(
                enrollment.id, enrollment.tenantId, result, criteria));
        }
    }

    /**
     * Determines the screening result from a list of criterion assessments.
     *
     * <p>Precedence: MARGINAL beats EXCLUDED (a marginal patient gets IRB review,
     * not silent exclusion). An empty criteria list returns CRITERIA_MET — this
     * assumes the REST layer validates that at least one criterion is present.
     */
    EligibilityScreeningResult determineResult(List<CriterionResult> criteria) {
        boolean anyMarginal = criteria.stream().anyMatch(CriterionResult::marginal);
        if (anyMarginal) return EligibilityScreeningResult.MARGINAL;
        boolean anyExcluded = criteria.stream().anyMatch(c -> !c.met());
        if (anyExcluded) return EligibilityScreeningResult.EXCLUDED;
        return EligibilityScreeningResult.CRITERIA_MET;
    }
}
