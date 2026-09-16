package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.VisitStatus;
import io.casehub.clinical.api.model.VisitType;

import java.time.Instant;
import java.util.UUID;

public record ScheduleVisitRequest(VisitType visitType, Instant visitDate,
                                    VisitStatus status, String notes) {}
