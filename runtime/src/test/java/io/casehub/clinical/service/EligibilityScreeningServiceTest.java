package io.casehub.clinical.service;

import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.model.EligibilityScreeningCaseStatus;
import io.casehub.clinical.api.model.EligibilityScreeningResult;
import io.casehub.clinical.entity.PatientEnrollment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EligibilityScreeningServiceTest {

    @Mock EligibilityScreeningLedgerWriter ledgerWriter;
    @Mock jakarta.enterprise.event.Event<io.casehub.clinical.api.EligibilityScreeningEvent> screeningEvents;
    @InjectMocks EligibilityScreeningService service;

    @Test
    void all_criteria_met_results_in_CRITERIA_MET() {
        var criteria = List.of(
            new CriterionResult("c1", true, false),
            new CriterionResult("c2", true, false)
        );
        assertThat(service.determineResult(criteria)).isEqualTo(EligibilityScreeningResult.CRITERIA_MET);
    }

    @Test
    void any_non_marginal_failed_results_in_EXCLUDED() {
        var criteria = List.of(
            new CriterionResult("c1", true, false),
            new CriterionResult("c2", false, false)
        );
        assertThat(service.determineResult(criteria)).isEqualTo(EligibilityScreeningResult.EXCLUDED);
    }

    @Test
    void any_marginal_results_in_MARGINAL() {
        var criteria = List.of(
            new CriterionResult("c1", true, false),
            new CriterionResult("c2", false, true)
        );
        assertThat(service.determineResult(criteria)).isEqualTo(EligibilityScreeningResult.MARGINAL);
    }

    @Test
    void marginal_takes_priority_over_excluded() {
        // One criterion is marginal (met=false, marginal=true),
        // another is excluded (met=false, marginal=false).
        // MARGINAL must win — a marginal patient goes to IRB; an excluded patient does not.
        var criteria = List.of(
            new CriterionResult("c1", false, true),  // marginal
            new CriterionResult("c2", false, false)  // excluded
        );
        assertThat(service.determineResult(criteria)).isEqualTo(EligibilityScreeningResult.MARGINAL);
    }

    @Test
    void non_marginal_screening_leaves_case_status_NONE() {
        var criteria = List.of(new CriterionResult("c1", true, false));

        PatientEnrollment enrollment = new PatientEnrollment();
        enrollment.id = UUID.randomUUID();
        enrollment.tenantId = "default";
        enrollment.eligibilityScreeningCaseStatus = EligibilityScreeningCaseStatus.NONE;

        service.screen(enrollment, criteria);

        assertThat(enrollment.eligibilityScreeningCaseStatus)
            .isEqualTo(EligibilityScreeningCaseStatus.NONE);
        verify(ledgerWriter).writeScreeningEntry(any(), any(), any());
    }
}
