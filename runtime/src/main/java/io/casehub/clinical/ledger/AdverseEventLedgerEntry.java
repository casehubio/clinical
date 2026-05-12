package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tamper-evident audit record written when an adverse event is reported.
 *
 * Extends LedgerEntry via JPA JOINED inheritance — base fields (subjectId,
 * sequenceNumber, actorId, occurredAt, digest) are in the ledger_entry table;
 * AE-specific fields are in ae_ledger_entry (V1005 migration).
 *
 * FDA IND requirement: every safety event must be independently verifiable
 * via the Merkle chain without server access.
 */
@Entity
@Table(name = "ae_ledger_entry")
@DiscriminatorValue("ADVERSE_EVENT")
public class AdverseEventLedgerEntry extends LedgerEntry {

    @Column(name = "adverse_event_id", nullable = false)
    public UUID adverseEventId;

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "ctcae_grade", nullable = false, length = 20)
    public String ctcaeGrade;

    @Column(name = "reported_at", nullable = false)
    public Instant reportedAt;

    @Column(name = "sla_deadline", nullable = false)
    public Instant slaDeadline;
}
