package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Tamper-evident audit record written when a protocol deviation is commanded
 * to the Principal Investigator for formal authorisation.
 *
 * Extends LedgerEntry via JPA JOINED inheritance — base fields (subjectId,
 * sequenceNumber, actorId, occurredAt, digest) are in the ledger_entry table;
 * deviation-specific fields are in protocol_deviation_ledger_entry (V1006 migration).
 *
 * GCP / ICH E6(R3) requirement: every protocol deviation must have a named PI
 * commitment with a tamper-evident record of when the command was issued and
 * the deadline by which a response is required.
 */
@Entity
@Table(name = "protocol_deviation_ledger_entry")
@DiscriminatorValue("PROTOCOL_DEVIATION")
public class ProtocolDeviationLedgerEntry extends LedgerEntry {

    @Column(name = "deviation_id")
    public UUID deviationId;

    @Column(name = "site_id")
    public UUID siteId;

    public String severity;

    @Column(name = "pi_id")
    public String piId;

    @Column(name = "commanded_at")
    public Instant commandedAt;

    @Column(name = "response_deadline")
    public Instant responseDeadline;

    @Column(name = "escalation_requirement")
    public String escalationRequirement;
}
