package io.casehub.clinical.resource;

import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.clinical.service.ProtocolAmendmentService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.UUID;

@Path("/trials/{trialId}/amendments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProtocolAmendmentResource {

    @Inject ProtocolAmendmentService service;
    @Inject CurrentPrincipal principal;

    public record ProposeAmendmentRequest(@NotBlank String proposedChange) {}

    public record AmendmentResponse(
        String id,
        String trialId,
        String proposedChange,
        String status,
        String amendmentCaseStatus,
        String proposedAt
    ) {}

    @POST
    public Response propose(@PathParam("trialId") UUID trialId,
                            @Valid ProposeAmendmentRequest req,
                            @Context UriInfo uriInfo) {
        // Validate trial exists and belongs to the caller's tenant
        ClinicalTrial trial = ClinicalTrial.findByIdForTenant(trialId, principal);
        if (trial == null)
            return Response.status(Response.Status.NOT_FOUND).build();

        ProtocolAmendment amendment = service.propose(trialId, req.proposedChange(),
            principal.tenancyId());
        return Response.created(
            uriInfo.getAbsolutePathBuilder().path(amendment.id.toString()).build()
        ).entity(toResponse(amendment)).build();
    }

    @GET
    @Path("/{amendmentId}")
    public Response get(@PathParam("trialId") UUID trialId,
                        @PathParam("amendmentId") UUID amendmentId) {
        ProtocolAmendment amendment = ProtocolAmendment.findByIdForTenant(amendmentId, principal);
        if (amendment == null || !amendment.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(toResponse(amendment)).build();
    }

    private AmendmentResponse toResponse(ProtocolAmendment a) {
        return new AmendmentResponse(
            a.id.toString(),
            a.trialId.toString(),
            a.proposedChange,
            a.status.name(),
            a.amendmentCaseStatus.name(),
            a.proposedAt.toString()
        );
    }
}
