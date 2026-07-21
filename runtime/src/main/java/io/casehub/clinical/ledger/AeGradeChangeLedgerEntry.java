package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;

@Entity
@Table(name = "ae_grade_change_ledger_entry")
@DiscriminatorValue("AE_GRADE_CHANGE")
public class AeGradeChangeLedgerEntry extends JpaLedgerEntry {

    @Column(name = "previous_grade")
    public String previousGrade;

    @Column(name = "new_grade", nullable = false)
    public String newGrade;

    @Column(length = 500)
    public String reason;

    @Column(name = "changed_by")
    public String changedBy;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
            previousGrade != null ? previousGrade : "",
            newGrade,
            reason != null ? reason : "",
            changedBy != null ? changedBy : ""
        ).getBytes(StandardCharsets.UTF_8);
    }
}
