package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.CriterionResult;

import java.util.List;

public record ScreenPatientRequest(List<CriterionResult> criteria) {}
