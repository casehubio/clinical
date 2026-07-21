package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.CtcaeGrade;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ae_grade_change")
public class AeGradeChange extends PanacheEntityBase {

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

    public static List<AeGradeChange> findByAdverseEventId(UUID aeId) {
        return list("adverseEventId = ?1 order by changedAt asc", aeId);
    }

    public static AeGradeChange findLatestByAdverseEventId(UUID aeId) {
        return find("adverseEventId = ?1 order by changedAt desc", aeId).firstResult();
    }
}
