package io.casehub.clinical.service;

import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.spi.DeviationContext;
import io.casehub.clinical.api.spi.DeviationResponsePolicy;
import io.casehub.clinical.api.spi.DeviationResponseRequirements;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@ApplicationScoped
@DefaultBean
public class DefaultDeviationResponsePolicy implements DeviationResponsePolicy {

    @ConfigProperty(name = "casehub.clinical.deviation.minor.deadline", defaultValue = "PT168H")
    Duration minorDeadline;

    @ConfigProperty(name = "casehub.clinical.deviation.major.deadline", defaultValue = "PT72H")
    Duration majorDeadline;

    @ConfigProperty(name = "casehub.clinical.deviation.critical.deadline", defaultValue = "PT24H")
    Duration criticalDeadline;

    @Override
    public DeviationResponseRequirements evaluate(DeviationContext context) {
        return switch (context.severity()) {
            case MINOR -> new DeviationResponseRequirements(minorDeadline, EscalationRequirement.NONE);
            case MAJOR -> new DeviationResponseRequirements(majorDeadline, EscalationRequirement.SPONSOR_NOTIFICATION);
            case CRITICAL -> new DeviationResponseRequirements(criticalDeadline, EscalationRequirement.IRB_REVIEW);
        };
    }
}
