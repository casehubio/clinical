package io.casehub.clinical.api;

import java.time.Instant;
import java.util.Map;

public record CascadeEvent(
    CascadeStepType step,
    CascadeStepStatus status,
    Instant timestamp,
    String actor,
    String detail,
    Map<String, Object> data
) {}
