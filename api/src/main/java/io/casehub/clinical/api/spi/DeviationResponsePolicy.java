package io.casehub.clinical.api.spi;

public interface DeviationResponsePolicy {
    DeviationResponseRequirements evaluate(DeviationContext context);
}
