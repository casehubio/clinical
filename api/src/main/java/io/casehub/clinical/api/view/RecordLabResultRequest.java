package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.AbnormalFlag;
import io.casehub.clinical.api.model.SpecimenType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecordLabResultRequest(String testName, BigDecimal value, String unit,
                                      BigDecimal referenceRangeLow, BigDecimal referenceRangeHigh,
                                      AbnormalFlag abnormalFlag, SpecimenType specimenType,
                                      String performingLab, Instant collectedAt, UUID visitId) {}
