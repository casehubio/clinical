package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.DrugAdminStatus;
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
import java.util.UUID;

@Entity
@Table(name = "study_drug_administration")
@NamedQuery(name = "StudyDrugAdministration.findByIdAndTenantId", query = "SELECT s FROM StudyDrugAdministration s WHERE s.id = :id AND s.tenantId = :tenantId")
@NamedQuery(name = "StudyDrugAdministration.listByEnrollment", query = "SELECT s FROM StudyDrugAdministration s WHERE s.enrollmentId = :enrollmentId AND s.tenantId = :tenantId")
@DynamicUpdate
public class StudyDrugAdministration {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "visit_id")
    public UUID visitId;

    @Column(name = "drug_name", nullable = false)
    public String drugName;

    @Column(nullable = false, length = 100)
    public String dose;

    @Column(nullable = false, length = 50)
    public String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public MedicationRoute route;

    @Column(name = "administered_at", nullable = false)
    public Instant administeredAt;

    @Column(name = "administered_by", nullable = false)
    public String administeredBy;

    @Column(name = "batch_number")
    public String batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    public DrugAdminStatus status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
