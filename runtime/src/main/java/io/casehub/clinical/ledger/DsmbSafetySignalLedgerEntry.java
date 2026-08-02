package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.jpa.JpaLedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Entity
@Table(name = "dsmb_safety_signal_ledger_entry")
@DiscriminatorValue("DSMB_SAFETY_SIGNAL")
public class DsmbSafetySignalLedgerEntry extends JpaLedgerEntry {

    @Column(name = "trial_id", nullable = false)
    public UUID trialId;

    @Column(name = "signal_type", nullable = false, length = 50)
    public String signalType;

    @Column(name = "affected_site_count", nullable = false)
    public int affectedSiteCount;

    @Column(length = 2048)
    public String summary;

    @Override
    protected byte[] domainContentBytes() {
        return String.join("|",
                trialId != null ? trialId.toString() : "",
                signalType != null ? signalType : "",
                String.valueOf(affectedSiteCount),
                summary != null ? summary : "")
                .getBytes(StandardCharsets.UTF_8);
    }
}
