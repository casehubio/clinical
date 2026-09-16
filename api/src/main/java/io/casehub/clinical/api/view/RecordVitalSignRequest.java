package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.VitalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecordVitalSignRequest(VitalType type, BigDecimal value, String unit,
                                      Instant measuredAt, UUID visitId) {}
