package io.casehub.clinical.resource;

import io.casehub.clinical.api.CascadeEvent;
import io.casehub.clinical.api.CascadeStepStatus;
import io.casehub.clinical.api.CascadeStepType;
import io.casehub.clinical.api.CascadeTemplateResolver;
import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.entity.AdverseEvent;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/adverse-events/{aeId}/cascade")
@Produces(MediaType.APPLICATION_JSON)
public class CascadeResource {

    @Inject CurrentPrincipal principal;

    @GET
    @RolesAllowed({"SPONSOR", "INVESTIGATOR", "COORDINATOR", "MONITOR"})
    public Response getCascade(@PathParam("aeId") UUID aeId) {
        AdverseEvent ae = AdverseEvent.findByIdForTenant(aeId, principal);
        if (ae == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Adverse event not found")).build();
        }

        List<CascadeStepType> template = CascadeTemplateResolver.resolve(
            ae.grade, ae.unexpected, ae.suspected);

        List<CascadeEvent> cascade = new ArrayList<>();
        for (CascadeStepType step : template) {
            cascade.add(reconstructStep(ae, step));
        }
        return Response.ok(cascade).build();
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
