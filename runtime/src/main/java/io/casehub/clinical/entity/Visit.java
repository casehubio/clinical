package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.VisitStatus;
import io.casehub.clinical.api.model.VisitType;
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
@Table(name = "visit")
@NamedQuery(name = "Visit.findByIdAndTenantId", query = "SELECT v FROM Visit v WHERE v.id = :id AND v.tenantId = :tenantId")
@NamedQuery(name = "Visit.listByEnrollment", query = "SELECT v FROM Visit v WHERE v.enrollmentId = :enrollmentId AND v.tenantId = :tenantId")
@DynamicUpdate
public class Visit {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false)
    public VisitType visitType;

    @Column(name = "visit_date", nullable = false)
    public Instant visitDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public VisitStatus status;

    @Column(length = 2000)
    public String notes;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
