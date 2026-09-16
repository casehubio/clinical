package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.MedicationFrequency;
import io.casehub.clinical.api.model.MedicationRoute;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MedicationView(UUID id, UUID enrollmentId, String medicationName,
                               String indication, String dose, String unit,
                               MedicationRoute route, MedicationFrequency frequency,
                               LocalDate startDate, LocalDate endDate, boolean ongoing,
                               Instant createdAt) {}
