package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.spi.AdverseEventContext;
import io.casehub.clinical.api.spi.AdverseEventEscalationPolicy;
import io.casehub.clinical.api.spi.AdverseEventEscalationRequirements;
import io.casehub.clinical.entity.AdverseEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Observes AdverseEventReportedEvent (Grade 3+ AEs) and starts an AE escalation engine case.
 *
 * <p>Three-phase pattern (same as TrialActivationService) keeps startCase().join() outside any
 * @Transactional boundary to avoid deadlocking the Agroal connection pool. Phase 4 markFailed()
 * fires on any exception from Phase 2–3 to avoid leaving escalationStatus stuck at REQUESTED.
 */
@ApplicationScoped
public class AeEscalationCaseService {

    private static final Logger LOG = Logger.getLogger(AeEscalationCaseService.class);
    private static final Set<CtcaeGrade> SEVERE_GRADES = Set.of(CtcaeGrade.GRADE_4, CtcaeGrade.GRADE_5);

    @Inject ClinicalAdverseEventCaseHub caseHub;
    @Inject AdverseEventEscalationPolicy policy;
    @Inject TrialSafetySignalService trialSafetySignalService;
    @Inject io.casehub.clinical.memory.ClinicalMemoryService memoryService;
    @Inject io.casehub.clinical.cbr.AeEscalationPlanRetriever planRetriever;
    @Inject
            io.casehub.clinical.cbr.AeTrajectoryAlertService  aeTrajectoryAlertService;


    public void onAdverseEventReported(@ObservesAsync AdverseEventReportedEvent event) {
        try {
            Map<String, Object> initialContext = prepareAndMarkRequested(event);
            if (initialContext == null) return;
            UUID caseId = caseHub.startCase(initialContext);
            persistCaseId(event.aeId(), caseId);
            try { aeTrajectoryAlertService.evaluate(event.aeId(), event.tenantId()); } catch (Exception te) { LOG.warnf(te, "Trajectory alert re-evaluation failed for aeId=%s", event.aeId()); }
            if (SEVERE_GRADES.contains(event.grade())) {
                trialSafetySignalService.signalGrade4Active(event.siteId());
            }
        } catch (Exception e) {
            LOG.errorf(e, "AeEscalationCaseService: escalation failed for aeId=%s — marking FAILED", event.aeId());
            try {
                markFailed(event.aeId());
            } catch (Exception markFailedEx) {
                LOG.errorf(markFailedEx, "AeEscalationCaseService: markFailed also failed for aeId=%s — status may be stuck at REQUESTED", event.aeId());
            }
        }
    }

    public void startEscalationForRegrade(UUID aeId, UUID enrollmentId, UUID siteId,
                                          io.casehub.clinical.api.model.CtcaeGrade grade, String tenantId) {
        try {
            Map<String, Object> initialContext = prepareAndMarkForRegrade(aeId, enrollmentId, siteId, grade, tenantId);
            if (initialContext == null) {return;}
            UUID caseId = caseHub.startCase(initialContext);
            persistCaseId(aeId, caseId);
            try {aeTrajectoryAlertService.evaluate(aeId, tenantId);} catch (Exception te) {
                LOG.warnf(te, "Trajectory alert re-evaluation failed for aeId=%s", aeId);
            }
            if (SEVERE_GRADES.contains(grade)) {
                trialSafetySignalService.signalGrade4Active(siteId);
            }
        } catch (Exception e) {
            LOG.errorf(e, "AeEscalationCaseService: regrade escalation failed for aeId=%s — marking FAILED", aeId);
            try {markFailed(aeId);} catch (Exception ex) {
                LOG.errorf(ex, "AeEscalationCaseService: markFailed also failed for aeId=%s", aeId);
            }
        }
    }

    @Transactional
    Map<String, Object> prepareAndMarkForRegrade(UUID aeId, UUID enrollmentId, UUID siteId,
                                                 io.casehub.clinical.api.model.CtcaeGrade grade, String tenantId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {
            LOG.warnf("AE not found for regrade escalation aeId=%s", aeId);
            return null;
        }
        if (ae.engineCaseId != null) {
            if (SEVERE_GRADES.contains(grade)) {
                trialSafetySignalService.signalGrade4Active(siteId);
            }
            return null;
        }
        if (ae.workItemId != null) {
            LOG.infof("Cancelling Grade 1/2 WorkItem %s for aeId=%s — engine case taking over", ae.workItemId, aeId);
            ae.workItemId = null;
        }
        ae.escalationStatus = AeEscalationStatus.REQUESTED;

        AdverseEventEscalationRequirements requirements = policy.evaluate(
                new AdverseEventContext(aeId, enrollmentId, siteId, grade));

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("aeId", aeId.toString());
        ctx.put("enrollmentId", enrollmentId.toString());
        ctx.put("siteId", siteId != null ? siteId.toString() : "");
        ctx.put("grade", grade.name());
        ctx.put("requiresSeniorMonitor", requirements.requiresSeniorMonitor());
        ctx.put("requiresDsmbEscalation", requirements.requiresDsmbEscalation());
        ctx.put("tenantId", tenantId);
        var patientCtx = memoryService.queryPatientContext(enrollmentId, tenantId);
        ctx.put("patientContext", patientCtx.toContextMap());
        if (siteId != null) {
            var siteCtx = memoryService.querySiteContext(siteId, tenantId);
            ctx.put("siteContext", siteCtx.toContextMap());
        }
        ctx.put("unexpected", ae.unexpected);
        ctx.put("suspected", ae.suspected);

        var plan = planRetriever.retrieve(ae);
        if (plan.hasRecommendation()) {
            ctx.put("escalationPlanRecommendation", plan.toContextMap());
        }
        return ctx;
    }


    @Transactional
    Map<String, Object> prepareAndMarkRequested(AdverseEventReportedEvent event) {
        AdverseEvent ae = AdverseEvent.findById(event.aeId());
        if (ae == null) {
            LOG.warnf("AeEscalationCaseService: AdverseEvent not found for aeId=%s — skipping escalation", event.aeId());
            return null;
        }
        ae.escalationStatus = AeEscalationStatus.REQUESTED;

        AdverseEventEscalationRequirements requirements = policy.evaluate(
                new AdverseEventContext(event.aeId(), event.enrollmentId(), event.siteId(), event.grade()));

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("aeId", event.aeId().toString());
        ctx.put("enrollmentId", event.enrollmentId().toString());
        ctx.put("siteId", event.siteId().toString());
        ctx.put("grade", event.grade().name());
        ctx.put("requiresSeniorMonitor", requirements.requiresSeniorMonitor());
        ctx.put("requiresDsmbEscalation", requirements.requiresDsmbEscalation());
        ctx.put("tenantId", ae.tenantId);
        var patientCtx = memoryService.queryPatientContext(ae.enrollmentId, ae.tenantId);
        var siteCtx    = memoryService.querySiteContext(event.siteId(), ae.tenantId);
        ctx.put("patientContext", patientCtx.toContextMap());
        ctx.put("siteContext", siteCtx.toContextMap());
        ctx.put("unexpected", ae.unexpected);
        ctx.put("suspected", ae.suspected);

        io.casehub.clinical.cbr.EscalationPlanRecommendation plan = planRetriever.retrieve(ae);
        if (plan.hasRecommendation()) {
            ctx.put("escalationPlanRecommendation", plan.toContextMap());
        }

        return ctx;
    }

    @Transactional
    void persistCaseId(UUID aeId, UUID caseId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {
            LOG.warnf("AeEscalationCaseService: AdverseEvent not found in Phase 3 for aeId=%s", aeId);
            return;
        }
        ae.engineCaseId = caseId;
    }

    @Transactional
    void markFailed(UUID aeId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {
            LOG.warnf("AeEscalationCaseService: AdverseEvent not found in markFailed for aeId=%s", aeId);
            return;
        }
        ae.escalationStatus = AeEscalationStatus.FAILED;
    }

}
