package io.casehub.clinical.service.api;

import io.casehub.clinical.api.CascadeEvent;
import io.casehub.clinical.api.CascadeStepStatus;
import io.casehub.clinical.api.CascadeStepType;
import io.casehub.clinical.api.CascadeTemplateResolver;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.spi.ClinicalCascadeApi;
import io.casehub.clinical.entity.AdverseEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class DefaultClinicalCascadeApi implements ClinicalCascadeApi {
    @jakarta.inject.Inject
    jakarta.persistence.EntityManager em;


    @Override
    public List<CascadeEvent> getCascade(UUID aeId, String tenancyId) {
        AdverseEvent ae = em.createNamedQuery("AdverseEvent.findByIdAndTenantId", AdverseEvent.class).setParameter("id", aeId).setParameter("tenantId", tenancyId).getResultStream().findFirst().orElse(null);
        if (ae == null) throw new NotFoundException("Adverse event not found: " + aeId);

        List<CascadeStepType> template = CascadeTemplateResolver.resolve(
                ae.grade, ae.unexpected, ae.suspected);

        List<CascadeEvent> cascade = new ArrayList<>();
        for (CascadeStepType step : template) {
            cascade.add(reconstructStep(ae, step));
        }
        return cascade;
    }

    private CascadeEvent reconstructStep(AdverseEvent ae, CascadeStepType step) {
        return switch (step) {
            case AE_REPORTED -> new CascadeEvent(step,
                    ae.reportedAt != null ? CascadeStepStatus.COMPLETED : CascadeStepStatus.PENDING,
                    ae.reportedAt, "system", "Adverse event reported",
                    Map.of("grade", ae.grade.name()));
            case SLA_ASSIGNED -> new CascadeEvent(step,
                    ae.slaDeadline != null ? CascadeStepStatus.COMPLETED : CascadeStepStatus.PENDING,
                    ae.reportedAt, "system", "SLA deadline assigned",
                    Map.of("slaHours", ae.grade.sla().orElseThrow().toHours()));
            case ESCALATION_CASE_STARTED -> new CascadeEvent(step,
                    ae.engineCaseId != null ? CascadeStepStatus.COMPLETED
                            : ae.escalationStatus == AeEscalationStatus.FAILED ? CascadeStepStatus.FAILED
                            : ae.escalationStatus == AeEscalationStatus.REQUESTED ? CascadeStepStatus.ACTIVE
                            : CascadeStepStatus.PENDING,
                    null, "engine", "Escalation case",
                    ae.engineCaseId != null ? Map.of("caseId", ae.engineCaseId.toString()) : Map.of());
            case REGULATORY_SUBMISSION_STARTED -> new CascadeEvent(step,
                    ae.regulatorySubmissionCaseId != null ? CascadeStepStatus.COMPLETED : CascadeStepStatus.PENDING,
                    null, "system", "IND regulatory submission",
                    ae.regulatorySubmissionCaseId != null ? Map.of("caseId", ae.regulatorySubmissionCaseId.toString()) : Map.of());
            default -> new CascadeEvent(step, CascadeStepStatus.PENDING,
                    null, null, step.name(), Map.of());
        };
    }
}
