package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.CtcaeGrade;

public record RegradeRequest(CtcaeGrade grade, String reason) {}
