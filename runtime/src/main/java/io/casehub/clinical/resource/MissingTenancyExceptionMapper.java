package io.casehub.clinical.resource;

import io.casehub.platform.api.identity.MissingTenancyException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;

@Provider
public class MissingTenancyExceptionMapper implements ExceptionMapper<MissingTenancyException> {

    @Override
    public Response toResponse(MissingTenancyException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of(
                        "error", "missing_tenancy_claim",
                        "message", "JWT does not contain required tenancyId claim",
                        "actorId", exception.actorId()))
                .build();
    }
}
