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
 * Tamper-evident record of safety officer adverse event notification delivery.
 * Records both successful and failed delivery attempts for GCP / FDA audit compliance.
 * JOINED inheritance on qhorus datasource. V1011.
 * Must live in io.casehub.clinical.ledger — never in io.casehub.clinical.entity.
 *
 * <p>ICH E6(R3) §5.17 / 21 CFR 312.32: the fact that the safety officer was (or was not)
 * notified must be independently verifiable in the tamper-evident audit trail.
 */
@Entity
@Table(name = "safety_officer_notification_ledger_entry")
@DiscriminatorValue("SafetyOfficerNotification")
public class SafetyOfficerNotificationLedgerEntry extends JpaLedgerEntry {

    @Column(name = "ae_id", nullable = false)
    public UUID aeId;

    @Column(name = "enrollment_id")
    public UUID enrollmentId;

    @Column(name = "site_id")
    public UUID siteId;

    @Column(name = "ctcae_grade", nullable = false)
    public String ctcaeGrade;

    @Column(name = "connector_id")
    public String connectorId;

    @Column(name = "destination", length = 2048)
    public String destination;

    @Column(name = "delivered", nullable = false)
    public boolean delivered;

    @Column(name = "notified_at", nullable = false)
    public Instant notifiedAt;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                aeId         != null ? aeId.toString()         : "",
                enrollmentId != null ? enrollmentId.toString() : "",
                siteId       != null ? siteId.toString()       : "",
                ctcaeGrade   != null ? ctcaeGrade              : "",
                connectorId  != null ? connectorId             : "",
                destination  != null ? destination             : "",
                String.valueOf(delivered),
                notifiedAt   != null ? notifiedAt.toString()   : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
