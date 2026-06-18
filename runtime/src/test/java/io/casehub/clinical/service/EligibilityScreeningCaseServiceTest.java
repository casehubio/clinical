package io.casehub.clinical.service;

import io.casehub.clinical.api.EligibilityScreeningEvent;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EligibilityScreeningCaseServiceTest {

    @Mock EligibilityScreeningCaseHub caseHub;
    @InjectMocks EligibilityScreeningCaseService service;

    private EligibilityScreeningEvent event(UUID enrollmentId) {
        return new EligibilityScreeningEvent(
            enrollmentId, "default", EligibilityScreeningResult.MARGINAL,
            List.of(new CriterionResult("c7", false, true))
        );
    }

    private PatientEnrollment enrollment(UUID id, EligibilityScreeningCaseStatus status) {
        PatientEnrollment e = new PatientEnrollment();
        e.id = id;
        e.tenantId = "default";
        e.eligibilityScreeningCaseStatus = status;
        return e;
    }

    @Test
    void phase1_idempotency_guard_returns_null_when_already_REQUESTED() {
        UUID id = UUID.randomUUID();
        PatientEnrollment e = enrollment(id, EligibilityScreeningCaseStatus.REQUESTED);
        Map<String, Object> ctx = service.prepareAndMark(event(id), e);
        assertThat(ctx).isNull();
        assertThat(e.eligibilityScreeningCaseStatus).isEqualTo(EligibilityScreeningCaseStatus.REQUESTED);
    }

    @Test
    void phase1_sets_REQUESTED_on_NONE_status() {
        UUID id = UUID.randomUUID();
        PatientEnrollment e = enrollment(id, EligibilityScreeningCaseStatus.NONE);
        Map<String, Object> ctx = service.prepareAndMark(event(id), e);
        assertThat(e.eligibilityScreeningCaseStatus).isEqualTo(EligibilityScreeningCaseStatus.REQUESTED);
        assertThat(ctx).isNotNull();
    }

    @Test
    void context_contains_enrollmentId_as_string() {
        UUID id = UUID.randomUUID();
        PatientEnrollment e = enrollment(id, EligibilityScreeningCaseStatus.NONE);
        Map<String, Object> ctx = service.prepareAndMark(event(id), e);
        assertThat(ctx.get("enrollmentId")).isEqualTo(id.toString());
    }

    @Test
    void context_serializes_criterion_results_as_maps_not_records() {
        UUID id = UUID.randomUUID();
        PatientEnrollment e = enrollment(id, EligibilityScreeningCaseStatus.NONE);
        Map<String, Object> ctx = service.prepareAndMark(event(id), e);
        @SuppressWarnings("unchecked")
        var list = (List<Map<String, Object>>) ctx.get("criteriaResults");
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsKey("id").containsKey("met").containsKey("marginal");
        assertThat(list.get(0).get("id")).isEqualTo("c7");
    }
}
