package io.casehub.clinical.api.view;

import java.time.LocalDate;

public record UpdateMedicationRequest(LocalDate endDate, Boolean ongoing) {}
