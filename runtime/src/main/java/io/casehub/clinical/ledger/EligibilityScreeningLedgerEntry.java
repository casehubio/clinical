package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Tamper-evident audit record written when a patient eligibility screening decision is made.
 *
 * Extends LedgerEntry via JPA JOINED inheritance — base fields (subjectId,
 * sequenceNumber, actorId, occurredAt, digest) are in the ledger_entry table;
 * screening-specific fields are in eligibility_screening_ledger_entry (V2024 migration).
 *
 * ICH E6(R3) §4.2 — eligibility assessment must be auditable and independently verifiable.
 */
@Entity
@Table(name = "eligibility_screening_ledger_entry")
@DiscriminatorValue("ELIGIBILITY_SCREENING")
public class EligibilityScreeningLedgerEntry extends JpaLedgerEntry {

    @Column(name = "enrollment_id", nullable = false)
    public UUID enrollmentId;

    @Column(name = "screening_result", nullable = false, length = 50)
    public String screeningResult;

    @Column(name = "criteria_count", nullable = false)
    public int criteriaCount;

    @Column(name = "marginal_count", nullable = false)
    public int marginalCount;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                enrollmentId != null ? enrollmentId.toString() : "",
                screeningResult != null ? screeningResult : "",
                String.valueOf(criteriaCount),
                String.valueOf(marginalCount))
                .getBytes(StandardCharsets.UTF_8);
    }
}
