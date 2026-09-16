package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;

import java.time.Instant;

public record ReportAdverseEventRequest(CtcaeGrade grade, Instant occurredAt,
                                          EventActuality actuality,
                                          Boolean unexpected, Boolean suspected) {}
