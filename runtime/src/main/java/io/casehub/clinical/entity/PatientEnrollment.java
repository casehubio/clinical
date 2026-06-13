package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EnrollmentStatus;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_enrollment")
public class PatientEnrollment extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "site_id", nullable = false)
    public UUID siteId;

    @Column(name = "patient_id", nullable = false)
    public String patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_status", nullable = false)
    public ConsentStatus consentStatus = ConsentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false)
    public EnrollmentStatus enrollmentStatus = EnrollmentStatus.CANDIDATE;

    @Column(name = "enrolled_at")
    public Instant enrolledAt;

    @Column(name = "withdrawn_at")
    public Instant withdrawnAt;

    public static PatientEnrollment findByIdForTenant(UUID id, CurrentPrincipal principal) {
        if (principal.isCrossTenantAdmin()) return findById(id);
        return find("id = ?1 AND tenantId = ?2", id, principal.tenancyId()).firstResult();
    }
}
