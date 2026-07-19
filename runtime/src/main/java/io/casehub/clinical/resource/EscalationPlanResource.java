package io.casehub.clinical.resource;

import io.casehub.clinical.cbr.AeEscalationPlanRetriever;
import io.casehub.clinical.cbr.EscalationPlanRecommendation;
import io.casehub.clinical.entity.AdverseEvent;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

import static io.casehub.clinical.api.ClinicalGroups.*;

@Path("/api/adverse-events/{aeId}/escalation-plans")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({SPONSOR, INVESTIGATOR, COORDINATOR, MONITOR})
public class EscalationPlanResource {

    @Inject AeEscalationPlanRetriever planRetriever;

    @GET
    @Transactional
    public Response getEscalationPlans(@PathParam("aeId") UUID aeId) {
        AdverseEvent ae = AdverseEvent.findById(aeId);
        if (ae == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        EscalationPlanRecommendation recommendation = planRetriever.retrieve(ae);
        return Response.ok(recommendation.toContextMap()).build();
    }
}
