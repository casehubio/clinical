package io.casehub.clinical.service;

import io.casehub.clinical.api.EligibilityScreeningEvent;
import io.casehub.clinical.api.model.EligibilityScreeningCaseStatus;
import io.casehub.clinical.entity.PatientEnrollment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Observes EligibilityScreeningEvent (MARGINAL results only) and starts an
 * eligibility screening engine case. Four-phase pattern per AeEscalationCaseService.
 */
@ApplicationScoped
public class EligibilityScreeningCaseService {

    private static final Logger LOG = Logger.getLogger(EligibilityScreeningCaseService.class);

    @Inject EligibilityScreeningCaseHub caseHub;

    public void onScreeningEvent(@ObservesAsync EligibilityScreeningEvent event) {
        try {
            Map<String, Object> ctx = prepareAndMark(event);
            if (ctx == null) return;
            // Phase 2 — startCase outside any TX boundary
            UUID caseId = caseHub.startCase(ctx);
            // Phase 3 — persist caseId
            persistCaseId(event.enrollmentId(), caseId);
        } catch (Exception e) {
            LOG.errorf(e, "EligibilityScreeningCaseService: failed for enrollmentId=%s", event.enrollmentId());
            try { markFailed(event.enrollmentId()); } catch (Exception ex) {
                LOG.errorf(ex, "EligibilityScreeningCaseService: markFailed also failed for enrollmentId=%s", event.enrollmentId());
            }
        }
    }

    @Transactional
    Map<String, Object> prepareAndMark(EligibilityScreeningEvent event) {
        // Phase 1 — load entity inside @Transactional so Hibernate manages the session
        // Uses findById (base Panache), not findByIdForTenant — @ObservesAsync runs off-request
        // with no @RequestScoped CurrentPrincipal active.
        PatientEnrollment enrollment = PatientEnrollment.findById(event.enrollmentId());
        if (enrollment == null) {
            LOG.warnf("EligibilityScreeningCaseService: enrollment not found %s", event.enrollmentId());
            return null;
        }
        if (enrollment.eligibilityScreeningCaseStatus != EligibilityScreeningCaseStatus.NONE) {
            LOG.debugf("EligibilityScreeningCaseService: already processed %s — skipping", event.enrollmentId());
            return null;
        }
        enrollment.eligibilityScreeningCaseStatus = EligibilityScreeningCaseStatus.REQUESTED;

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("enrollmentId", event.enrollmentId().toString());
        ctx.put("tenantId", event.tenantId());
        ctx.put("screeningResult", event.screeningResult().name());
        // Serialize CriterionResult records to plain maps — JQ evaluator requires JSON-compatible types
        ctx.put("criteriaResults", event.criteriaResults().stream()
            .map(c -> Map.<String, Object>of("id", c.id(), "met", c.met(), "marginal", c.marginal()))
            .toList());
        return ctx;
    }

    @Transactional
    void persistCaseId(UUID enrollmentId, UUID caseId) {
        PatientEnrollment e = PatientEnrollment.findById(enrollmentId);
        if (e != null) e.eligibilityEngineCaseId = caseId;
    }

    @Transactional
    void markFailed(UUID enrollmentId) {
        PatientEnrollment e = PatientEnrollment.findById(enrollmentId);
        if (e != null) e.eligibilityScreeningCaseStatus = EligibilityScreeningCaseStatus.FAILED;
    }
}
