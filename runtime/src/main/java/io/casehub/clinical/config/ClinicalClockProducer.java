package io.casehub.clinical.config;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/** Supplies a UTC clock when an upstream runtime does not provide one. */
@ApplicationScoped
public class ClinicalClockProducer {

    @Produces
    @DefaultBean
    @ApplicationScoped
    public Clock clock() {
        return Clock.systemUTC();
    }
}
