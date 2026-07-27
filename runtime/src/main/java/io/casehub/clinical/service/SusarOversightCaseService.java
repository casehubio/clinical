package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.clinical.entity.AdverseEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Observes AdverseEventReportedEvent concurrently with AeEscalationCaseService.
 * Starts a SUSAR oversight case when the evaluator confirms criteria are met.
 *
 * <p>Three-phase pattern keeps startCase().join() outside any @Transactional boundary
 * to avoid deadlocking the Agroal pool (same as TrialActivationService).
 */
@ApplicationScoped
public class SusarOversightCaseService {

    private static final Logger LOG = Logger.getLogger(SusarOversightCaseService.class);

    @Inject ClinicalSusarOversightCaseHub susarOversightCaseHub;
    @Inject SusarEvaluatorFunction susarEvaluator;
    @Inject
            io.casehub.clinical.cbr.AeTrajectoryAlertService aeTrajectoryAlertService;


    public void onAdverseEventReported(@ObservesAsync AdverseEventReportedEvent event) {
        try {
            Map<String, Object> initialContext = prepareAndMark(event);
            if (initialContext == null) return;
            UUID caseId = susarOversightCaseHub.startCase(initialContext);
            persistCaseId(event.aeId(), caseId);
            try { aeTrajectoryAlertService.evaluate(event.aeId(), event.tenantId()); } catch (Exception te) { LOG.warnf(te, "Trajectory alert re-evaluation failed for aeId=%s", event.aeId()); }
        } catch (Exception e) {
            LOG.errorf(e, "SusarOversightCaseService: oversight case failed for aeId=%s", event.aeId());
            try {
                markFailed(event.aeId());
            } catch (Exception ex) {
                LOG.errorf(ex, "SusarOversightCaseService: markFailed also failed for aeId=%s", event.aeId());
            }
        }
    }

    public void reevaluateForRegrade(UUID aeId, UUID siteId, String tenantId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {return;}
        if (ae.susarOversightStatus != SusarOversightStatus.NONE) {return;}
        if (!ae.unexpected || !ae.suspected) {return;}

        var event = new AdverseEventReportedEvent(
                aeId, ae.enrollmentId, siteId, ae.grade, ae.reportedAt, tenantId);
        try {
            Map<String, Object> ctx = prepareAndMark(event);
            if (ctx == null) {return;}
            UUID caseId = susarOversightCaseHub.startCase(ctx);
            persistCaseId(aeId, caseId);
        } catch (Exception e) {
            LOG.errorf(e, "SusarOversightCaseService: regrade evaluation failed for aeId=%s", aeId);
            markFailed(aeId);
        }
    }


    @Transactional
    Map<String, Object> prepareAndMark(AdverseEventReportedEvent event) {
        AdverseEvent ae = AdverseEvent.findById(event.aeId());
        if (ae == null) {
            LOG.warnf("SusarOversightCaseService: AE not found for aeId=%s — skipping", event.aeId());
            return null;
        }
        // Idempotency guard — protects against CDI at-least-once re-delivery
        if (ae.susarOversightStatus != SusarOversightStatus.NONE) {
            LOG.debugf("SusarOversightCaseService: aeId=%s already processed (status=%s) — skipping",
                    event.aeId(), ae.susarOversightStatus);
            return null;
        }
        // Criteria check via SPI — honours CDI displacement for ML-based evaluator
        var result = susarEvaluator.apply(Map.of("aeId", ae.id.toString()));
        boolean susarRequired = Boolean.TRUE.equals(result.output().get("susarRequired"));
        if (!susarRequired) {
            LOG.debugf("SusarOversightCaseService: SUSAR criteria not met for aeId=%s — no oversight case",
                    event.aeId());
            return null;
        }
        ae.susarOversightStatus = SusarOversightStatus.REQUESTED;
        // Build initial context for the oversight case
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("aeId", ae.id.toString());
        ctx.put("grade", ae.grade.name());
        ctx.put("unexpected", ae.unexpected);
        ctx.put("suspected", ae.suspected);
        ctx.put("enrollmentId", ae.enrollmentId.toString());
        ctx.put("siteId", event.siteId().toString());
        ctx.put("tenantId", ae.tenantId);
        ctx.put("susarOversight", true); // discriminator for SusarOversightListener
        return ctx;
    }

    @Transactional
    void persistCaseId(UUID aeId, UUID caseId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {
            LOG.warnf("SusarOversightCaseService: AE not found in Phase 3 for aeId=%s", aeId);
            return;
        }
        ae.susarOversightCaseId = caseId;
    }

    @Transactional
    void markFailed(UUID aeId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) return;
        ae.susarOversightStatus = SusarOversightStatus.FAILED;
    }
}
