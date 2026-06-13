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
 * Tamper-evident record for SUSAR oversight gate decisions (approved, rejected, expired).
 * FDA IND / EU AI Act Art.12: clinician gate decisions on SUSAR criteria must be
 * independently verifiable. JOINED inheritance on qhorus datasource. V2021.
 *
 * {@code subjectId = enrollmentId} — required for LedgerProvExportService.exportSubject()
 * to include this entry in the patient's PROV-DM audit export.
 */
@Entity
@Table(name = "susar_decision_ledger_entry")
@DiscriminatorValue("SusarDecision")
public class SusarDecisionLedgerEntry extends LedgerEntry {

    @Column(name = "ae_id", nullable = false)
    public UUID aeId;

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "ctcae_grade", length = 20)
    public String ctcaeGrade;

    @Column(name = "gate_outcome", nullable = false, length = 20)
    public String gateOutcome;

    @Column(name = "decided_at", nullable = false)
    public Instant decidedAt;

    @Column(name = "decided_by", length = 255)
    public String decidedBy;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                aeId         != null ? aeId.toString()         : "",
                enrollmentId != null ? enrollmentId.toString() : "",
                ctcaeGrade   != null ? ctcaeGrade              : "",
                gateOutcome  != null ? gateOutcome             : "",
                decidedAt    != null ? decidedAt.toString()    : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
