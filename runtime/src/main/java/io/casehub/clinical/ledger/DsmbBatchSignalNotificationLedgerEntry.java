package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dsmb_batch_signal_notification_ledger_entry")
@DiscriminatorValue("DSMB_BATCH_SIGNAL_NOTIFICATION")
public class DsmbBatchSignalNotificationLedgerEntry extends JpaLedgerEntry {

    @Column(name = "trial_id", nullable = false)
    public UUID trialId;

    @Column(name = "signal_type", nullable = false, length = 50)
    public String signalType;

    @Column(name = "work_item_id")
    public UUID workItemId;

    @Column(name = "connector_id")
    public String connectorId;

    @Column(name = "destination", length = 2048)
    public String destination;

    @Column(name = "delivered", nullable = false)
    public boolean delivered;

    @Column(name = "failure_reason", length = 2048)
    public String failureReason;

    @Column(name = "notified_at", nullable = false)
    public Instant notifiedAt;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                trialId != null ? trialId.toString() : "",
                signalType != null ? signalType : "",
                workItemId != null ? workItemId.toString() : "",
                connectorId != null ? connectorId : "",
                destination != null ? destination : "",
                String.valueOf(delivered),
                failureReason != null ? failureReason : "",
                notifiedAt != null ? notifiedAt.toString() : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
