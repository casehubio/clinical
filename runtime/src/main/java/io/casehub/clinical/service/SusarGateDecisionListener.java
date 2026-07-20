package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.internal.event.ActionGateApprovedEvent;
import io.casehub.engine.common.internal.event.ActionGateExpiredEvent;
import io.casehub.engine.common.internal.event.ActionGateRejectedEvent;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Writes ledger entries for all three SUSAR oversight gate outcomes.
 *
 * <p>Gate discrimination uses DB query (AdverseEvent.findBySusarOversightCaseId) — not
 * CaseInstanceCache, which is racy: the engine's gate handlers clear pendingActionGate
 * before the clinical listener sees the event. The DB approach is race-free and survives
 * JVM restart (engine#433).
 *
 * <p>For rejected/expired: signals both susarAssessmentComplete and susarRequired to the
 * oversight case. The second signal may arrive at an already-completed case; the engine
 * discards it with a WARN log — this is benign.
 */
@ApplicationScoped
public class SusarGateDecisionListener {

    private static final Logger LOG = Logger.getLogger(SusarGateDecisionListener.class);

    @Inject ClinicalSusarOversightCaseHub susarOversightCaseHub;
    @Inject SusarDecisionLedgerWriter ledgerWriter;
    @Inject
            io.casehub.clinical.cbr.AeTrajectoryAlertService aeTrajectoryAlertService;


    @ConsumeEvent(value = "casehub.action.gate.approved", blocking = true)
    public void onApproved(ActionGateApprovedEvent event) {
        AdverseEvent ae = AdverseEvent.findBySusarOversightCaseId(event.caseId());
        if (ae == null) return; // not a SUSAR oversight gate
        LOG.infof("SusarGateDecisionListener: gate APPROVED caseId=%s aeId=%s", event.caseId(), ae.id);
        // No case signalling — engine's ActionGateApprovedHandler calls refireCompletion(),
        // which writes deferred worker output (susarAssessmentComplete: true) via
        // WorkflowExecutionCompletedHandler, satisfying the susar-complete goal.
        ledgerWriter.writeEntry(ae, "APPROVED", Instant.now(), event.approvedBy());
        try { aeTrajectoryAlertService.evaluate(ae.id, ae.tenantId); } catch (Exception te) { LOG.warnf(te, "Trajectory alert evaluation failed for aeId=%s", ae.id); }
    }

    @ConsumeEvent(value = "casehub.action.gate.rejected", blocking = true)
    public void onRejected(ActionGateRejectedEvent event) {
        AdverseEvent ae = AdverseEvent.findBySusarOversightCaseId(event.caseId());
        if (ae == null) return;
        LOG.infof("SusarGateDecisionListener: gate REJECTED caseId=%s aeId=%s", event.caseId(), ae.id);
        susarOversightCaseHub.signal(event.caseId(), "susarAssessmentComplete", true);
        susarOversightCaseHub.signal(event.caseId(), "susarRequired", false);
        ledgerWriter.writeEntry(ae, "REJECTED", Instant.now(), event.rejectedBy());
        try { aeTrajectoryAlertService.evaluate(ae.id, ae.tenantId); } catch (Exception te) { LOG.warnf(te, "Trajectory alert evaluation failed for aeId=%s", ae.id); }
    }

    @ConsumeEvent(value = "casehub.action.gate.expired", blocking = true)
    public void onExpired(ActionGateExpiredEvent event) {
        AdverseEvent ae = AdverseEvent.findBySusarOversightCaseId(event.caseId());
        if (ae == null) return;
        LOG.infof("SusarGateDecisionListener: gate EXPIRED caseId=%s aeId=%s", event.caseId(), ae.id);
        susarOversightCaseHub.signal(event.caseId(), "susarAssessmentComplete", true);
        susarOversightCaseHub.signal(event.caseId(), "susarRequired", false);
        ledgerWriter.writeEntry(ae, "EXPIRED", Instant.now(), ClinicalActors.CLINICAL_SERVICE);
        try { aeTrajectoryAlertService.evaluate(ae.id, ae.tenantId); } catch (Exception te) { LOG.warnf(te, "Trajectory alert evaluation failed for aeId=%s", ae.id); }
    }
}
