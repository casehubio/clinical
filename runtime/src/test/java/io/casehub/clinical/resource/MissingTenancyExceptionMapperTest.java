package io.casehub.clinical.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.platform.api.identity.MissingTenancyException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class MissingTenancyExceptionMapperTest {

    private final MissingTenancyExceptionMapper mapper = new MissingTenancyExceptionMapper();

    @Test
    void maps_to_400_with_json_body() {
        var exception = new MissingTenancyException("user-abc");
        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
        @SuppressWarnings("unchecked")
        var body = (java.util.Map<String, String>) response.getEntity();
        assertThat(body.get("error")).isEqualTo("missing_tenancy_claim");
        assertThat(body.get("actorId")).isEqualTo("user-abc");
        assertThat(body.get("message")).contains("tenancyId");
    }
}
