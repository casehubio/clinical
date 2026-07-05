package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Tamper-evident record for IND expedited safety report actually filed (WorkItem completed).
 *
 * <p>Written by RegulatorySubmissionLedgerWriter.writeFiledEntry() when the IND reporting
 * WorkItem transitions to DONE — confirming submission within the 21 CFR 312.32 deadline.
 * EU AI Act Art.12 compliance supplement attached at write time.
 *
 * <p>JOINED inheritance on qhorus datasource. V2026.
 *
 * <p>subjectId = ae.enrollmentId (Merkle chain continuity across all AE-related entries).
 *
 * <p>{@code domainContentBytes()} uses aeId + grade + submittedAt — all stable identifiers
 * that survive any subsequent erasure or pseudonymization.
 */
@Entity
@Table(name = "ind_report_filed_ledger_entry")
@DiscriminatorValue("IndReportFiled")
public class IndReportFiledLedgerEntry extends JpaLedgerEntry {

    @Column(name = "ae_id", nullable = false)
    public UUID aeId;

    @Column(name = "grade", nullable = false, length = 20)
    public String grade;

    @Column(name = "submitted_at", nullable = false)
    public Instant submittedAt;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                aeId        != null ? aeId.toString()        : "",
                grade       != null ? grade                  : "",
                submittedAt != null ? submittedAt.toString() : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
