package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.VisitStatus;

public record UpdateVisitRequest(VisitStatus status, String notes) {}
