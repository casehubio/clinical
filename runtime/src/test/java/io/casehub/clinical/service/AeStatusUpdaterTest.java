package io.casehub.clinical.service;

import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.platform.testing.FixedCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AeStatusUpdaterTest {

    @Inject AeStatusUpdater statusUpdater;
    @Inject FixedCurrentPrincipal principal;
    @Inject
            jakarta.persistence.EntityManager em;


    private UUID aeId;
    private UUID caseId;

    @BeforeEach
    @Transactional
    void setup() {
        aeId = UUID.randomUUID();
        caseId = UUID.randomUUID();

        AdverseEvent ae = new AdverseEvent();
        ae.id = aeId;
        ae.enrollmentId = UUID.randomUUID();
        ae.tenantId = principal.tenancyId();
        ae.grade = CtcaeGrade.GRADE_3;
        ae.actuality = EventActuality.ACTUAL;
        ae.outcome = AeOutcome.ONGOING;
        ae.occurredAt = Instant.now();
        ae.reportedAt = Instant.now();
        ae.escalationStatus = AeEscalationStatus.REQUESTED;
        ae.engineCaseId = caseId;
        em.persist(ae);
    }

    @Test
    void matching_caseId_returns_COMPLETED() {
        var result = statusUpdater.markCompleted(aeId, caseId);
        assertThat(result).isEqualTo(AeStatusUpdater.CompletionResult.COMPLETED);
    }

    @Test
    void mismatched_caseId_returns_SUPERSEDED() {
        var result = statusUpdater.markCompleted(aeId, UUID.randomUUID());
        assertThat(result).isEqualTo(AeStatusUpdater.CompletionResult.SUPERSEDED);
    }

    @Test
    void null_engineCaseId_with_non_null_expected_returns_SUPERSEDED() {
        setEngineCaseId(null);
        var result = statusUpdater.markCompleted(aeId, UUID.randomUUID());
        assertThat(result).isEqualTo(AeStatusUpdater.CompletionResult.SUPERSEDED);
    }

    @Test
    void already_completed_returns_ALREADY_COMPLETED() {
        setEscalationStatus(AeEscalationStatus.COMPLETED);
        var result = statusUpdater.markCompleted(aeId, caseId);
        assertThat(result).isEqualTo(AeStatusUpdater.CompletionResult.ALREADY_COMPLETED);
    }

    @Test
    void nonexistent_ae_returns_NOT_FOUND() {
        var result = statusUpdater.markCompleted(UUID.randomUUID(), caseId);
        assertThat(result).isEqualTo(AeStatusUpdater.CompletionResult.NOT_FOUND);
    }

    @Transactional
    void setEscalationStatus(AeEscalationStatus status) {
        AdverseEvent ae = em.find(AdverseEvent.class, aeId);
        ae.escalationStatus = status;
    }

    @Transactional
    void setEngineCaseId(UUID id) {
        AdverseEvent ae = em.find(AdverseEvent.class, aeId);
        ae.engineCaseId = id;
    }
}
