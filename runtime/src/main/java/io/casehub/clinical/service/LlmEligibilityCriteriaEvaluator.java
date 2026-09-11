package io.casehub.clinical.service;

import io.casehub.clinical.agent.ClinicalAgentRequest;
import io.casehub.clinical.agent.ClinicalAgentResult;
import io.casehub.clinical.agent.ClinicalAgentSupport;
import io.casehub.clinical.agent.EligibilityResponse;
import io.casehub.clinical.api.model.CriterionResult;
import io.casehub.clinical.api.spi.EligibilityCriteriaEvaluator;
import io.casehub.clinical.entity.ConcomitantMedication;
import io.casehub.clinical.entity.LabResult;
import io.casehub.clinical.entity.PatientEnrollment;
import io.casehub.clinical.entity.VitalSign;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@ApplicationScoped
public class LlmEligibilityCriteriaEvaluator implements EligibilityCriteriaEvaluator {

    private static final Logger LOG = Logger.getLogger(LlmEligibilityCriteriaEvaluator.class);

    static final String SYSTEM_PROMPT = """
            You are a clinical trial eligibility screening expert with expertise in ICH E6(R3) and \
            protocol-specific inclusion/exclusion criteria evaluation.

            Given a patient's clinical data and a list of protocol criteria, evaluate each criterion \
            independently. For each criterion, determine:
            - met: true if the patient clearly satisfies the criterion
            - marginal: true if the patient is borderline or the data is insufficient to make a \
              definitive determination — flag edge cases as marginal rather than excluded (conservative)

            When both met=false and marginal=true, the patient is borderline — this triggers IRB \
            consultation for human review rather than silent exclusion.

            Respond with JSON only:
            {"criteria":[{"criterionId":"criterion-0","met":true,"marginal":false,"reasoning":"..."},\
            {"criterionId":"criterion-1","met":false,"marginal":true,"reasoning":"..."}]}
            """;

    private final ClinicalAgentSupport agentSupport;

    @Inject
    public LlmEligibilityCriteriaEvaluator(ClinicalAgentSupport agentSupport) {
        this.agentSupport = agentSupport;
    }

    @Override
    public List<CriterionResult> evaluate(UUID enrollmentId, String tenantId, List<String> protocolCriteria) {
        List<CriterionResult> fallback = buildFallback(protocolCriteria);
        String userPrompt = buildUserPrompt(enrollmentId, tenantId, protocolCriteria);

        var request = new ClinicalAgentRequest<>(
                SYSTEM_PROMPT, userPrompt, EligibilityResponse.class,
                new EligibilityResponse(List.of()),
                "eligibility", enrollmentId != null ? enrollmentId.toString() : null);

        ClinicalAgentResult<EligibilityResponse> result = agentSupport.invoke(request);

        if (result.fallbackUsed() || result.response().criteria() == null || result.response().criteria().isEmpty()) {
            LOG.warnf("LlmEligibilityCriteriaEvaluator: fallback used — %s", result.failureReason());
            return fallback;
        }

        return result.response().criteria().stream()
                .map(c -> new CriterionResult(c.criterionId(), c.met(), c.marginal()))
                .toList();
    }

    private List<CriterionResult> buildFallback(List<String> protocolCriteria) {
        return IntStream.range(0, protocolCriteria.size())
                .mapToObj(i -> new CriterionResult("criterion-" + i, false, true))
                .toList();
    }

    String buildUserPrompt(UUID enrollmentId, String tenantId, List<String> protocolCriteria) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Protocol Criteria\n\n");
        for (int i = 0; i < protocolCriteria.size(); i++) {
            sb.append("- criterion-").append(i).append(": ").append(protocolCriteria.get(i)).append("\n");
        }

        try {
            PatientEnrollment enrollment = PatientEnrollment.findById(enrollmentId);
            if (enrollment != null) {
                sb.append("\n## Patient Data\n");
                sb.append("- Patient ID: ").append(enrollment.patientId).append("\n");
                sb.append("- Enrollment status: ").append(enrollment.enrollmentStatus).append("\n");
                sb.append("- Treatment arm: ").append(enrollment.treatmentArm != null ? enrollment.treatmentArm : "not assigned").append("\n");

                appendLabResults(sb, enrollmentId, tenantId);
                appendVitalSigns(sb, enrollmentId, tenantId);
                appendConcomitantMedications(sb, enrollmentId, tenantId);
            }
        } catch (Exception e) {
            LOG.debugf("Could not load patient data for prompt enrichment: %s", e.getMessage());
        }

        return sb.toString();
    }

    private void appendLabResults(StringBuilder sb, UUID enrollmentId, String tenantId) {
        List<LabResult> labs = LabResult.listByEnrollment(enrollmentId, tenantId);
        if (!labs.isEmpty()) {
            sb.append("\n### Lab Results\n");
            for (LabResult lab : labs) {
                sb.append("- ").append(lab.testName).append(": ").append(lab.value).append(" ").append(lab.unit);
                if (lab.referenceRangeLow != null && lab.referenceRangeHigh != null) {
                    sb.append(" (ref: ").append(lab.referenceRangeLow).append("-").append(lab.referenceRangeHigh).append(")");
                }
                sb.append(" [").append(lab.abnormalFlag).append("]\n");
            }
        }
    }

    private void appendVitalSigns(StringBuilder sb, UUID enrollmentId, String tenantId) {
        List<VitalSign> vitals = VitalSign.listByEnrollment(enrollmentId, tenantId);
        if (!vitals.isEmpty()) {
            sb.append("\n### Vital Signs\n");
            for (VitalSign v : vitals) {
                sb.append("- ").append(v.type).append(": ").append(v.value).append(" ").append(v.unit).append("\n");
            }
        }
    }

    private void appendConcomitantMedications(StringBuilder sb, UUID enrollmentId, String tenantId) {
        List<ConcomitantMedication> meds = ConcomitantMedication.listByEnrollment(enrollmentId, tenantId);
        if (!meds.isEmpty()) {
            sb.append("\n### Concomitant Medications\n");
            for (ConcomitantMedication med : meds) {
                sb.append("- ").append(med.medicationName).append(" ").append(med.dose).append(" ").append(med.unit)
                        .append(" ").append(med.route).append(" ").append(med.frequency);
                if (med.indication != null) sb.append(" (").append(med.indication).append(")");
                sb.append(med.ongoing ? " [ongoing]" : " [completed]").append("\n");
            }
        }
    }
}
