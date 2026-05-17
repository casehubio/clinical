package io.casehub.clinical.api.spi;

import io.casehub.clinical.api.model.EscalationRequirement;
import java.time.Duration;

public record DeviationResponseRequirements(
    Duration piResponseDeadline,
    EscalationRequirement escalationRequirement
) {}
