package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.api.model.AmendmentPrecedentResponse;
import io.casehub.clinical.cbr.ClinicalCbrDomains;
import io.casehub.clinical.cbr.ClinicalCbrService;
import io.casehub.clinical.entity.ClinicalTrial;
import io.casehub.clinical.entity.ProtocolAmendment;
import io.casehub.clinical.service.ProtocolAmendmentService;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/trials/{trialId}/amendments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProtocolAmendmentResource {

    @Inject ProtocolAmendmentService service;
    @Inject CurrentPrincipal principal;
    @Inject ClinicalCbrService cbrService;

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
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR})
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
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR, ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response get(@PathParam("trialId") UUID trialId,
                        @PathParam("amendmentId") UUID amendmentId) {
        ProtocolAmendment amendment = ProtocolAmendment.findByIdForTenant(amendmentId, principal);
        if (amendment == null || !amendment.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(toResponse(amendment)).build();
    }

    @GET
    @Path("/{amendmentId}/precedents")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.INVESTIGATOR,
                   ClinicalGroups.COORDINATOR, ClinicalGroups.MONITOR})
    public Response amendmentPrecedents(@PathParam("trialId") UUID trialId,
                                        @PathParam("amendmentId") UUID amendmentId) {
        // Load amendment
        ProtocolAmendment amendment = ProtocolAmendment.findByIdForTenant(amendmentId, principal);
        if (amendment == null || !amendment.trialId.equals(trialId))
            return Response.status(Response.Status.NOT_FOUND).build();

        // Query with empty features (Phase 1 - no text embeddings yet)
        CbrQuery query = CbrQuery.of(
            principal.tenancyId(),
            ClinicalCbrDomains.AMENDMENT,
            "clinical-amendment",
            Map.of(),  // Empty features for textual cases
            10
        ).withVectorWeight(0.0);

        List<AmendmentPrecedentResponse> results = cbrService.retrieveSimilar(query, TextualCbrCase.class)
            .stream()
            .map(this::mapToAmendmentResponse)
            .toList();

        return Response.ok(results).build();
    }

    private AmendmentPrecedentResponse mapToAmendmentResponse(ScoredCbrCase<TextualCbrCase> scored) {
        TextualCbrCase c = scored.cbrCase();
        // Phase 1: all returned with score 1.0 (no text embeddings)
        return new AmendmentPrecedentResponse(
            scored.score(),
            c.problem(),
            c.solution(),
            c.outcome()
        );
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
