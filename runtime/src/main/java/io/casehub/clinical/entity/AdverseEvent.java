package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.AeEscalationStatus;
import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.casehub.clinical.api.model.RegulatorySubmissionStatus;
import io.casehub.clinical.api.model.SusarOversightStatus;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "adverse_event")
@DynamicUpdate
public class AdverseEvent extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CtcaeGrade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EventActuality actuality = EventActuality.ACTUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AeOutcome outcome = AeOutcome.ONGOING;

    @Column(name = "event_type")
    public String eventType;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "reported_at", nullable = false)
    public Instant reportedAt;

    /** Computed as reportedAt + grade.sla(). Present for all grades per GCP ICH E6(R3) §5.17. */
    @Column(name = "sla_deadline")
    public Instant slaDeadline;

    /** WorkItem id created by AdverseEventService for GCP SLA tracking. Null until service call. */
    @Column(name = "work_item_id")
    public UUID workItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_status", nullable = false)
    public AeEscalationStatus escalationStatus = AeEscalationStatus.NONE;

    @Column(name = "engine_case_id")
    public UUID engineCaseId;

    @Column(nullable = false)
    public boolean unexpected = false;

    /** Conservative default per ICH E2A §I.A.1: all AEs assumed IMP-suspected unless explicitly false. */
    @Column(nullable = false)
    public boolean suspected = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "susar_oversight_status", nullable = false)
    public SusarOversightStatus susarOversightStatus = SusarOversightStatus.NONE;

    @Column(name = "susar_oversight_case_id")
    public UUID susarOversightCaseId;

    @Column(name = "regulatory_submission_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    public RegulatorySubmissionStatus regulatorySubmissionStatus = RegulatorySubmissionStatus.NONE;

    @Column(name = "regulatory_submission_case_id")
    public UUID regulatorySubmissionCaseId;

    public static AdverseEvent findByIdForTenant(UUID id, CurrentPrincipal principal) {
        if (principal.isCrossTenantAdmin()) return findById(id);
        return find("id = ?1 AND tenantId = ?2", id, principal.tenancyId()).firstResult();
    }

    public static AdverseEvent findBySusarOversightCaseId(UUID caseId) {
        return find("susarOversightCaseId", caseId).firstResult();
    }
}
