package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.MedicationFrequency;
import io.casehub.clinical.api.model.MedicationRoute;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "concomitant_medication")
@NamedQuery(name = "ConcomitantMedication.findByIdAndTenantId", query = "SELECT m FROM ConcomitantMedication m WHERE m.id = :id AND m.tenantId = :tenantId")
@NamedQuery(name = "ConcomitantMedication.listByEnrollment", query = "SELECT m FROM ConcomitantMedication m WHERE m.enrollmentId = :enrollmentId AND m.tenantId = :tenantId")
@DynamicUpdate
public class ConcomitantMedication {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "medication_name", nullable = false)
    public String medicationName;

    @Column(length = 500)
    public String indication;

    @Column(nullable = false, length = 100)
    public String dose;

    @Column(nullable = false, length = 50)
    public String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public MedicationRoute route;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public MedicationFrequency frequency;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    @Column(nullable = false)
    public boolean ongoing = true;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
