package io.casehub.clinical.demo;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Set;

/**
 * Dev-mode CurrentPrincipal providing a fixed demo tenant and actor.
 *
 * <p>Active only in dev profile via {@code @IfBuildProfile("dev")}. Priority 150
 * ensures it displaces {@code MockCurrentPrincipal} (no priority) and {@code OidcCurrentPrincipal}
 * ({@code @Priority(100)}) when running in dev mode.
 */
@ApplicationScoped
@Alternative
@Priority(150)
@IfBuildProfile("dev")
public class DemoCurrentPrincipal implements CurrentPrincipal {

    public static final String TENANT_ID = "demo-tenant";
    public static final String ACTOR_ID = "demo-user";

    @Override
    public String tenancyId() {
        return TENANT_ID;
    }

    @Override
    public String actorId() {
        return ACTOR_ID;
    }

    @Override
    public Set<String> groups() {
        // Demo user has all clinical roles for unrestricted dev-mode access
        return Set.of("SPONSOR", "INVESTIGATOR", "COORDINATOR", "MONITOR");
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return false;
    }
}
