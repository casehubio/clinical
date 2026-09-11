package io.casehub.clinical.api.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EvaluateScreenRequest(
        @NotNull @Size(min = 1, message = "At least one protocol criterion is required")
        List<String> protocolCriteria) {}
