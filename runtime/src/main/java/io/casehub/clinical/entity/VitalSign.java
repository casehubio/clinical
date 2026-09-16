package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.VitalType;
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
@Table(name = "vital_sign")
@NamedQuery(name = "VitalSign.findByIdAndTenantId", query = "SELECT v FROM VitalSign v WHERE v.id = :id AND v.tenantId = :tenantId")
@NamedQuery(name = "VitalSign.listByEnrollment", query = "SELECT v FROM VitalSign v WHERE v.enrollmentId = :enrollmentId AND v.tenantId = :tenantId")
@DynamicUpdate
public class VitalSign {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "visit_id")
    public UUID visitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vital_type", nullable = false, length = 50)
    public VitalType type;

    @Column(name = "result_value", nullable = false, precision = 19, scale = 4)
    public BigDecimal value;

    @Column(nullable = false, length = 50)
    public String unit;

    @Column(name = "measured_at", nullable = false)
    public Instant measuredAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
