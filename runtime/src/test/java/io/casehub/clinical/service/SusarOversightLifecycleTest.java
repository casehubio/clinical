package io.casehub.clinical.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for SusarOversightCaseService three-phase lifecycle.
 */
@QuarkusTest
class SusarOversightLifecycleTest {

    @Inject SusarOversightCaseService service;

    @Test
    void grade4_unexpected_suspected_starts_oversight_case() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, true, true);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_4);

        service.onAdverseEventReported(event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            AdverseEvent ae = findAe(aeId);
            assertThat(ae.susarOversightStatus).isIn(
                    SusarOversightStatus.REQUESTED, SusarOversightStatus.COMPLETED);
            assertThat(ae.susarOversightCaseId).isNotNull();
        });
    }

    @Test
    void grade2_does_not_start_oversight_case() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_2, false, true);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_2);

        service.onAdverseEventReported(event);

        AdverseEvent ae = findAe(aeId);
        assertThat(ae.susarOversightStatus).isEqualTo(SusarOversightStatus.NONE);
        assertThat(ae.susarOversightCaseId).isNull();
    }

    @Test
    void idempotency_guard_prevents_double_start() {
        UUID aeId = persistAe(CtcaeGrade.GRADE_4, true, true);
        setStatus(aeId, SusarOversightStatus.REQUESTED);
        AdverseEventReportedEvent event = buildEvent(aeId, CtcaeGrade.GRADE_4);

        service.onAdverseEventReported(event);

        assertThat(findAe(aeId).susarOversightCaseId).isNull();
    }

    @Transactional
    UUID persistAe(CtcaeGrade grade, boolean unexpected, boolean suspected) {
        UUID aeId = UUID.randomUUID();
        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.enrollmentId = UUID.randomUUID();
        ae.grade = grade;
        ae.unexpected = unexpected;
        ae.suspected = suspected;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.tenantId = "test-tenant";
        ae.persist();
        return aeId;
    }

    @Transactional
    void setStatus(UUID aeId, SusarOversightStatus status) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        ae.susarOversightStatus = status;
    }

    @Transactional
    AdverseEvent findAe(UUID aeId) {
        return AdverseEvent.findById(aeId);
    }

    AdverseEventReportedEvent buildEvent(UUID aeId, CtcaeGrade grade) {
        return new AdverseEventReportedEvent(
                aeId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                grade,
                Instant.now(),
                "test-tenant");
    }
}
