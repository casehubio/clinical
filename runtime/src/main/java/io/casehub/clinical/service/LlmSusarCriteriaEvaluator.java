package io.casehub.clinical.service;

import io.casehub.clinical.agent.ClinicalAgentRequest;
import io.casehub.clinical.agent.ClinicalAgentResult;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.clinical.agent.SusarAssessmentResponse;
import io.casehub.clinical.api.model.ClinicalActionType;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.clinical.entity.ConcomitantMedication;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.worker.api.PlannedAction;
import io.casehub.worker.api.WorkerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class LlmSusarCriteriaEvaluator implements SusarEvaluatorFunction {
    @Inject
    EntityManager em;


    private static final Logger LOG = Logger.getLogger(LlmSusarCriteriaEvaluator.class);

    static final String SYSTEM_PROMPT = """
            You are a clinical safety scientist specializing in SUSAR assessment per ICH E2A \
            and 21 CFR 312.32.

            Evaluate whether an adverse event meets SUSAR criteria: serious, unexpected, and \
            suspected causal relationship to study drug. Consider the full clinical context — \
            not just grade thresholds.

            Reason about:
            - Causality: temporal relationship, dose-response, dechallenge/rechallenge, known drug class effects
            - Expectedness: against the Investigator's Brochure reference safety profile

            Evaluate only Grade 4 and Grade 5 adverse events. Grade 3 unexpected AEs are out of scope.

            Respond with JSON only:
            {"susarRequired":true,"causalityAssessment":"probable","expectednessAssessment":"unexpected",\
            "reasoning":"one paragraph","confidence":0.85}
            """;

    private final ClinicalAgentSupport agentSupport;

    @Inject
    public LlmSusarCriteriaEvaluator(ClinicalAgentSupport agentSupport) {
        this.agentSupport = agentSupport;
    }

    @Override
    @Transactional
    public WorkerResult<Map<String, Object>> apply(Map<String, Object> context) {
        String aeIdStr = (String) context.get("aeId");
        if (aeIdStr == null) {
            LOG.warn("LlmSusarCriteriaEvaluator: missing aeId — conservative fallback");
            return fallbackResult(null);
        }

        UUID aeId;
        try {
            aeId = UUID.fromString(aeIdStr);
        } catch (IllegalArgumentException e) {
            return fallbackResult(null);
        }

        String userPrompt = buildUserPrompt(aeId);

        var request = new ClinicalAgentRequest<>(
                SYSTEM_PROMPT, userPrompt, SusarAssessmentResponse.class,
                new SusarAssessmentResponse(true, "unknown", "unknown", "LLM unavailable — conservative fallback", 0f),
                "safety", aeIdStr, null);

        ClinicalAgentResult<SusarAssessmentResponse> result = agentSupport.invoke(request);

        if (result.fallbackUsed()) {
            LOG.warnf("LlmSusarCriteriaEvaluator: fallback used — %s", result.failureReason());
            return fallbackResult(aeIdStr);
        }

        SusarAssessmentResponse response = result.response();
        Map<String, Object> output = new HashMap<>();
        output.put("susarRequired", response.susarRequired());
        output.put("susarAssessmentComplete", true);
        output.put("causalityAssessment", response.causalityAssessment());
        output.put("reasoning", response.reasoning());

        if (response.susarRequired()) {
            Map<String, Object> actionCtx = new HashMap<>();
            actionCtx.put("aeId", aeIdStr);
            actionCtx.put("causalityAssessment", response.causalityAssessment());
            return WorkerResult.of(output,
                    PlannedAction.of(
                            ClinicalActionType.SUSAR_CRITERIA_DECISION.reason(),
                            ClinicalActionType.SUSAR_CRITERIA_DECISION.actionType(),
                            actionCtx));
        }

        return WorkerResult.of(output);
    }

    private WorkerResult<Map<String, Object>> fallbackResult(String aeIdStr) {
        Map<String, Object> output = new HashMap<>();
        output.put("susarRequired", true);
        output.put("susarAssessmentComplete", true);

        Map<String, Object> actionCtx = new HashMap<>();
        if (aeIdStr != null) actionCtx.put("aeId", aeIdStr);
        actionCtx.put("fallback", true);
        return WorkerResult.of(output,
                PlannedAction.of(
                        ClinicalActionType.SUSAR_CRITERIA_DECISION.reason(),
                        ClinicalActionType.SUSAR_CRITERIA_DECISION.actionType(),
                        actionCtx));
    }

    String buildUserPrompt(UUID aeId) {
        StringBuilder sb = new StringBuilder();
        try {
            AdverseEvent ae = em.find(AdverseEvent.class, aeId);
            if (ae != null) {
                sb.append("## Adverse Event\n");
                sb.append("- Grade: ").append(ae.grade).append("\n");
                sb.append("- Event type: ").append(ae.eventType != null ? ae.eventType : "unspecified").append("\n");
                sb.append("- Unexpected: ").append(ae.unexpected).append("\n");
                sb.append("- Suspected causal: ").append(ae.suspected).append("\n");
                sb.append("- Actuality: ").append(ae.actuality).append("\n");
                sb.append("- Outcome: ").append(ae.outcome).append("\n");
                sb.append("- Occurred at: ").append(ae.occurredAt).append("\n");
                sb.append("- Reported at: ").append(ae.reportedAt).append("\n");

                PatientEnrollment enrollment = em.find(PatientEnrollment.class, ae.enrollmentId);
                if (enrollment != null) {
                    sb.append("\n## Patient Context\n");
                    sb.append("- Patient ID: ").append(enrollment.patientId).append("\n");
                    sb.append("- Treatment arm: ").append(enrollment.treatmentArm != null ? enrollment.treatmentArm : "not assigned").append("\n");

                    List<ConcomitantMedication> meds = em.createNamedQuery("ConcomitantMedication.listByEnrollment", ConcomitantMedication.class).setParameter("enrollmentId", ae.enrollmentId).setParameter("tenantId", enrollment.tenantId).getResultList();
                    if (!meds.isEmpty()) {
                        sb.append("\n### Concomitant Medications\n");
                        for (ConcomitantMedication med : meds) {
                            sb.append("- ").append(med.medicationName).append(" ").append(med.dose)
                                    .append(" ").append(med.unit).append(" ").append(med.route).append("\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf("Could not load AE data for prompt enrichment: %s", e.getMessage());
        }

        if (sb.isEmpty()) {
            sb.append("## Adverse Event\n- AE ID: ").append(aeId).append("\n- No further data available\n");
        }

        return sb.toString();
    }
}
