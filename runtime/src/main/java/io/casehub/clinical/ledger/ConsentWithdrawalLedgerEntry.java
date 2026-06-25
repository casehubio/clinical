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
 * Tamper-evident record for GDPR Art.17 consent withdrawal.
 *
 * {@code actorId = enrollmentId.toString()} at write time; LedgerErasureService
 * pseudonymizes this field post-erasure. UUID-only domainContentBytes ensures
 * the Merkle chain survives erasure. JOINED inheritance on qhorus datasource. V2022.
 */
@Entity
@Table(name = "consent_withdrawal_ledger_entry")
@DiscriminatorValue("ConsentWithdrawal")
public class ConsentWithdrawalLedgerEntry extends LedgerEntry {

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "withdrawn_at", nullable = false)
    public Instant withdrawnAt;

    @Column(name = "ledger_entries_affected", nullable = false)
    public long ledgerEntriesAffected = 0L;

    @Column(name = "memories_erased", nullable = false)
    public boolean memoriesErased = false;

    @Column(name = "receipt_entry_id")
    public UUID receiptEntryId;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                enrollmentId != null ? enrollmentId.toString() : "",
                withdrawnAt  != null ? withdrawnAt.toString()  : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
