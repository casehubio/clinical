package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.DeviationSeverity;

public record ReportDeviationRequest(String deviationType, DeviationSeverity severity) {}
