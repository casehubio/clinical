package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.ConsentStatus;
import io.casehub.clinical.api.model.EligibilityScreeningCaseStatus;
import io.casehub.clinical.api.model.EligibilityScreeningResult;
import io.casehub.clinical.api.model.EnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_enrollment")
@NamedQuery(name = "PatientEnrollment.findByIdAndTenantId", query = "SELECT e FROM PatientEnrollment e WHERE e.id = :id AND e.tenantId = :tenantId")
public class PatientEnrollment {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "screening_result")
    public EligibilityScreeningResult screeningResult;

    @Column(name = "screening_completed_at")
    public Instant screeningCompletedAt;

    @Column(name = "eligibility_engine_case_id")
    public UUID eligibilityEngineCaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_screening_case_status", nullable = false)
    public EligibilityScreeningCaseStatus eligibilityScreeningCaseStatus = EligibilityScreeningCaseStatus.NONE;
    @Column(name = "treatment_arm")
    public String                         treatmentArm;

}
