package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.DrugAdminStatus;
import io.casehub.clinical.api.model.MedicationRoute;

import java.time.Instant;
import java.util.UUID;

public record StudyDrugView(UUID id, UUID enrollmentId, String drugName,
                              String dose, String unit, MedicationRoute route,
                              Instant administeredAt, String administeredBy,
                              String batchNumber, DrugAdminStatus status,
                              Instant createdAt) {}
