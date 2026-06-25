package io.casehub.clinical.resource;

import io.casehub.clinical.api.ClinicalGroups;
import io.casehub.clinical.service.GdprErasureService;
import io.casehub.clinical.service.PatientNotFoundException;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

@Path("/api/gdpr/erasure")
public class GdprErasureResource {

    @Inject GdprErasureService erasureService;
    @Inject CurrentPrincipal principal;

    @DELETE
    @Path("/patients/{patientId}")
    @RolesAllowed({ClinicalGroups.SPONSOR, ClinicalGroups.COORDINATOR})
    public Response erasePatient(@PathParam("patientId") String patientId) {
        try {
            int count = erasureService.erasePatient(patientId, principal.tenancyId());
            return Response.noContent()
                    .header("X-Enrollments-Erased", String.valueOf(count))
                    .build();
        } catch (PatientNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
