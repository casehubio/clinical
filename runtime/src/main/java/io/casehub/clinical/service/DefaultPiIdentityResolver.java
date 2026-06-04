package io.casehub.clinical.service;

import io.casehub.clinical.api.spi.PiIdentityResolver;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@DefaultBean
public class DefaultPiIdentityResolver implements PiIdentityResolver {

    @PostConstruct
    void warnIfDefaultActive() {
        if (!LaunchMode.current().isDevOrTest()) {
            Log.warn("PiIdentityResolver: using default passthrough — sponsor notifications " +
                     "will contain raw PI actor IDs. Provide a custom implementation.");
        }
    }

    @Override
    public String resolveFormalName(String actorId) {
        return actorId;
    }
}
