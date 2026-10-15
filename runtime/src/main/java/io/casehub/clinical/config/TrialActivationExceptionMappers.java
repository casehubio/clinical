package io.casehub.clinical.config;

import io.casehub.clinical.service.TrialActivationService;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TrialActivationExceptionMappers {

    @Provider
    public static class NotFoundMapper implements ExceptionMapper<TrialActivationService.TrialNotFoundException> {
        @Override
        public Response toResponse(TrialActivationService.TrialNotFoundException e) {
            return Response.status(404).entity(e.getMessage()).build();
        }
    }

    @Provider
    public static class ConflictMapper implements ExceptionMapper<TrialActivationService.TrialNotInPlanningStatusException> {
        @Override
        public Response toResponse(TrialActivationService.TrialNotInPlanningStatusException e) {
            return Response.status(409).entity(e.getMessage()).build();
        }
    }
}
