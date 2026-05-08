package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.AeOutcome;
import io.casehub.clinical.api.model.CtcaeGrade;
import io.casehub.clinical.api.model.EventActuality;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adverse_event")
public class AdverseEvent extends PanacheEntityBase {

    @Id
    public UUID id;

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

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "reported_at", nullable = false)
    public Instant reportedAt;

    /** Null for Grade 1 and 2 (no GCP SLA). Computed from reportedAt + grade.sla(). */
    @Column(name = "sla_deadline")
    public Instant slaDeadline;
}
