package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.MedicationFrequency;
import io.casehub.clinical.api.model.MedicationRoute;

import java.time.LocalDate;

public record RecordMedicationRequest(String medicationName, String indication,
                                       String dose, String unit, MedicationRoute route,
                                       MedicationFrequency frequency, LocalDate startDate,
                                       LocalDate endDate, boolean ongoing) {}
