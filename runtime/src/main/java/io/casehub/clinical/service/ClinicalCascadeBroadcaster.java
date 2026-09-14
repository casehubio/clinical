package io.casehub.clinical.service;

import io.casehub.clinical.api.AdverseEventReportedEvent;
import io.casehub.clinical.api.AeEscalationFailedEvent;
import io.casehub.clinical.api.AeEscalationStartedEvent;
import io.casehub.clinical.api.CascadeEvent;
import io.casehub.clinical.api.CascadeStepStatus;
import io.casehub.clinical.api.CascadeStepType;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.engine.common.internal.event.ActionGateApprovedEvent;
import io.casehub.engine.common.internal.event.ActionGateExpiredEvent;
import io.casehub.engine.common.internal.event.ActionGateRejectedEvent;
import io.casehub.pages.push.EventBroadcaster;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ClinicalCascadeBroadcaster {

    private static final Logger LOG = Logger.getLogger(ClinicalCascadeBroadcaster.class);

    private final EventBroadcaster eventBroadcaster;

    @Inject
    public ClinicalCascadeBroadcaster(EventBroadcaster eventBroadcaster) {
        this.eventBroadcaster = eventBroadcaster;
    }

    public void onAeReported(@ObservesAsync AdverseEventReportedEvent event) {
        broadcastStep(event.aeId(), CascadeStepType.AE_REPORTED, CascadeStepStatus.COMPLETED,
            "system", "Grade " + event.grade().label() + " adverse event reported",
            Map.of("grade", event.grade().name()));
        broadcastStep(event.aeId(), CascadeStepType.SLA_ASSIGNED, CascadeStepStatus.COMPLETED,
            "system", "SLA deadline: " + event.grade().sla().orElseThrow().toHours() + "h",
            Map.of("slaHours", event.grade().sla().orElseThrow().toHours()));
    }

    public void onEscalationStarted(@ObservesAsync AeEscalationStartedEvent event) {
        broadcastStep(event.aeId(), CascadeStepType.ESCALATION_CASE_STARTED, CascadeStepStatus.COMPLETED,
            "engine", "Escalation case created", Map.of("caseId", event.caseId().toString()));
    }

    public void onEscalationFailed(@ObservesAsync AeEscalationFailedEvent event) {
        broadcastStep(event.aeId(), CascadeStepType.ESCALATION_CASE_STARTED, CascadeStepStatus.FAILED,
            "engine", "Escalation failed: " + event.errorMessage(), Map.of());
    }

    @ConsumeEvent(value = "casehub.action.gate.approved", blocking = true)
    public void onGateApproved(ActionGateApprovedEvent event) {
        AdverseEvent ae = AdverseEvent.findBySusarOversightCaseId(event.caseId());
        if (ae == null) return;
        broadcastStep(ae.id, CascadeStepType.GATE_OPENED, CascadeStepStatus.COMPLETED,
            "system", "SUSAR oversight gate opened", Map.of());
        broadcastStep(ae.id, CascadeStepType.GATE_RESOLVED, CascadeStepStatus.COMPLETED,
            "PI: " + event.approvedBy(), "Gate approved",
            Map.of("decision", "approved", "approvedBy", event.approvedBy()));
    }

    @ConsumeEvent(value = "casehub.action.gate.rejected", blocking = true)
    public void onGateRejected(ActionGateRejectedEvent event) {
        AdverseEvent ae = AdverseEvent.findBySusarOversightCaseId(event.caseId());
        if (ae == null) return;
        broadcastStep(ae.id, CascadeStepType.GATE_OPENED, CascadeStepStatus.COMPLETED,
            "system", "SUSAR oversight gate opened", Map.of());
        broadcastStep(ae.id, CascadeStepType.GATE_RESOLVED, CascadeStepStatus.COMPLETED,
            "system", "Gate rejected", Map.of("decision", "rejected"));
    }

    @ConsumeEvent(value = "casehub.action.gate.expired", blocking = true)
    public void onGateExpired(ActionGateExpiredEvent event) {
        AdverseEvent ae = AdverseEvent.findBySusarOversightCaseId(event.caseId());
        if (ae == null) return;
        broadcastStep(ae.id, CascadeStepType.GATE_OPENED, CascadeStepStatus.COMPLETED,
            "system", "SUSAR oversight gate opened", Map.of());
        broadcastStep(ae.id, CascadeStepType.GATE_RESOLVED, CascadeStepStatus.FAILED,
            "system", "Gate expired — no PI response", Map.of("decision", "expired"));
    }

    public void agentSelected(UUID aeId, String agentId, double trustScore) {
        broadcastStep(aeId, CascadeStepType.AGENT_SELECTED, CascadeStepStatus.COMPLETED,
            agentId, "Agent selected with trust score " + String.format("%.2f", trustScore),
            Map.of("agentId", agentId, "trustScore", trustScore));
    }

    public void agentReasoning(UUID aeId, String configKey) {
        broadcastStep(aeId, CascadeStepType.AGENT_REASONING, CascadeStepStatus.ACTIVE,
            configKey, "Agent reasoning in progress", Map.of("configKey", configKey));
    }

    public void agentResult(UUID aeId, String configKey, boolean success) {
        broadcastStep(aeId, CascadeStepType.AGENT_RESULT,
            success ? CascadeStepStatus.COMPLETED : CascadeStepStatus.FAILED,
            configKey, success ? "Agent completed" : "Agent failed — using fallback",
            Map.of("configKey", configKey, "success", success));
    }

    public void safetyOfficerNotified(UUID aeId) {
        broadcastStep(aeId, CascadeStepType.SAFETY_OFFICER_NOTIFIED, CascadeStepStatus.COMPLETED,
            "system", "Safety officer notification sent", Map.of());
    }

    public void regulatorySubmissionStarted(UUID aeId, UUID caseId) {
        broadcastStep(aeId, CascadeStepType.REGULATORY_SUBMISSION_STARTED, CascadeStepStatus.COMPLETED,
            "system", "IND regulatory submission case started",
            Map.of("caseId", caseId.toString()));
    }

    public void ledgerSealed(UUID aeId, String digest) {
        broadcastStep(aeId, CascadeStepType.LEDGER_SEALED, CascadeStepStatus.COMPLETED,
            "ledger", "Merkle entry sealed",
            Map.of("digest", digest != null ? digest : ""));
    }

    public void broadcastGrade12Cascade(UUID aeId, CtcaeGrade grade, Instant slaDeadline) {
        broadcastStep(aeId, CascadeStepType.AE_REPORTED, CascadeStepStatus.COMPLETED,
            "system", "Grade " + grade.label() + " adverse event reported",
            Map.of("grade", grade.name()));
        broadcastStep(aeId, CascadeStepType.SLA_ASSIGNED, CascadeStepStatus.COMPLETED,
            "system", "SLA deadline: " + grade.sla().orElseThrow().toHours() + "h",
            Map.of("slaHours", grade.sla().orElseThrow().toHours()));
        broadcastStep(aeId, CascadeStepType.LEDGER_SEALED, CascadeStepStatus.COMPLETED,
            "ledger", "Initial report ledger entry sealed", Map.of());
    }

    private void broadcastStep(UUID aeId, CascadeStepType step, CascadeStepStatus status,
                               String actor, String detail, Map<String, Object> data) {
        String topic = "clinical:ae:" + aeId + ":cascade";
        try {
            eventBroadcaster.broadcast(topic, new CascadeEvent(step, status, Instant.now(), actor, detail, data));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to broadcast cascade step %s for aeId=%s", step, aeId);
        }
    }
}
