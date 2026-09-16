package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.AbnormalFlag;
import io.casehub.clinical.api.model.SpecimenType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lab_result")
@NamedQuery(name = "LabResult.findByIdAndTenantId", query = "SELECT r FROM LabResult r WHERE r.id = :id AND r.tenantId = :tenantId")
@NamedQuery(name = "LabResult.listByEnrollment", query = "SELECT r FROM LabResult r WHERE r.enrollmentId = :enrollmentId AND r.tenantId = :tenantId")
@DynamicUpdate
public class LabResult {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "visit_id")
    public UUID visitId;

    @Column(name = "test_name", nullable = false)
    public String testName;

    @Column(name = "result_value", nullable = false, precision = 19, scale = 4)
    public BigDecimal value;

    @Column(nullable = false, length = 50)
    public String unit;

    @Column(name = "reference_range_low", precision = 19, scale = 4)
    public BigDecimal referenceRangeLow;

    @Column(name = "reference_range_high", precision = 19, scale = 4)
    public BigDecimal referenceRangeHigh;

    @Enumerated(EnumType.STRING)
    @Column(name = "abnormal_flag", nullable = false, length = 50)
    public AbnormalFlag abnormalFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "specimen_type", nullable = false, length = 50)
    public SpecimenType specimenType;

    @Column(name = "performing_lab")
    public String performingLab;

    @Column(name = "collected_at", nullable = false)
    public Instant collectedAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
