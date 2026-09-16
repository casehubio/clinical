package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.CtcaeGrade;
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
@Table(name = "ae_grade_change")
@NamedQuery(name = "AeGradeChange.findByAdverseEventId", query = "SELECT g FROM AeGradeChange g WHERE g.adverseEventId = :aeId ORDER BY g.changedAt ASC")
@NamedQuery(name = "AeGradeChange.findLatestByAdverseEventId", query = "SELECT g FROM AeGradeChange g WHERE g.adverseEventId = :aeId ORDER BY g.changedAt DESC")
public class AeGradeChange {

    @Id
    public UUID id;

    @Column(name = "adverse_event_id", nullable = false)
    public UUID adverseEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_grade")
    public CtcaeGrade previousGrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_grade", nullable = false)
    public CtcaeGrade newGrade;

    @Column(name = "changed_at", nullable = false)
    public Instant changedAt;

    @Column(name = "changed_by", nullable = false)
    public String changedBy;

    @Column(length = 500)
    public String reason;
}
