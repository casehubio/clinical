package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Tamper-evident audit record written at each lifecycle transition of a protocol amendment.
 *
 * Extends LedgerEntry via JPA JOINED inheritance — base fields (subjectId,
 * sequenceNumber, actorId, occurredAt, digest) are in the ledger_entry table;
 * amendment-specific fields are in protocol_amendment_ledger_entry (V2025 migration).
 *
 * 21 CFR Part 312 §312.30 — protocol amendments must be independently verifiable
 * from proposal through approval or rejection.
 */
@Entity
@Table(name = "protocol_amendment_ledger_entry")
@DiscriminatorValue("PROTOCOL_AMENDMENT")
public class ProtocolAmendmentLedgerEntry extends LedgerEntry {

    @Column(name = "amendment_id", nullable = false)
    public UUID amendmentId;

    @Column(name = "trial_id", nullable = false)
    public UUID trialId;

    @Column(name = "proposed_change", nullable = false, columnDefinition = "TEXT")
    public String proposedChange;

    @Column(name = "status", nullable = false, length = 50)
    public String status;

    @Column(name = "supervisor_recommendation", length = 50)
    public String supervisorRecommendation;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                amendmentId != null ? amendmentId.toString() : "",
                trialId != null ? trialId.toString() : "",
                status != null ? status : "",
                proposedChange != null ? proposedChange : "",
                Objects.toString(supervisorRecommendation, ""))
                .getBytes(StandardCharsets.UTF_8);
    }
}
