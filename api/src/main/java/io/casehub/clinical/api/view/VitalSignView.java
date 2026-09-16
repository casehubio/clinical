package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.VitalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VitalSignView(UUID id, UUID enrollmentId, UUID visitId,
                              VitalType type, BigDecimal value, String unit,
                              Instant measuredAt, Instant createdAt) {}
