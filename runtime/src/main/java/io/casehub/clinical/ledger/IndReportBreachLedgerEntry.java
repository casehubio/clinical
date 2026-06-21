package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Tamper-evident record for IND reporting deadline exhausted without submission.
 *
 * <p>Written by RegulatorySubmissionLedgerWriter.writeBreachEntry() when the IND reporting
 * WorkItem reaches ESCALATED terminal state — confirming the 21 CFR 312.32 deadline was
 * not met. EU AI Act Art.12 compliance supplement attached at write time.
 *
 * <p>{@code breachReason} is a fixed string — WorkItemLifecycleEvent.detail() is always
 * null for ESCALATED events, so the reason is hardcoded at write time rather than sourced
 * from the event.
 *
 * <p>JOINED inheritance on qhorus datasource. V2027.
 *
 * <p>subjectId = ae.enrollmentId (Merkle chain continuity across all AE-related entries).
 *
 * <p>{@code domainContentBytes()} uses aeId + grade + breachedAt only — breachReason is
 * a fixed string, not domain identity, and excluded to keep the hash stable.
 */
@Entity
@Table(name = "ind_report_breach_ledger_entry")
@DiscriminatorValue("IndReportBreach")
public class IndReportBreachLedgerEntry extends LedgerEntry {

    @Column(name = "ae_id", nullable = false)
    public UUID aeId;

    @Column(name = "grade", nullable = false, length = 20)
    public String grade;

    @Column(name = "breached_at", nullable = false)
    public Instant breachedAt;

    @Column(name = "breach_reason", length = 255)
    public String breachReason;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                aeId       != null ? aeId.toString()       : "",
                grade      != null ? grade                 : "",
                breachedAt != null ? breachedAt.toString() : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
