package io.casehub.clinical.api.view;

import io.casehub.clinical.api.model.DrugAdminStatus;
import io.casehub.clinical.api.model.MedicationRoute;

import java.time.Instant;

public record RecordDrugAdminRequest(String drugName, String dose, String unit,
                                      MedicationRoute route, Instant administeredAt,
                                      String administeredBy, String batchNumber,
                                      DrugAdminStatus status) {}
