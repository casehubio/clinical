package io.casehub.clinical.ledger;

import io.casehub.ledger.runtime.model.LedgerEntry;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Tamper-evident per-attempt sponsor notification audit record.
 *
 * <p>subjectId = notificationId — the notification entity is the audit subject,
 * keeping the per-attempt chain isolated from the deviation's lifecycle chain.
 * deviationId is stored for cross-reference: query by deviationId to find all attempts.
 *
 * <p>JOINED inheritance on qhorus datasource. Migration V2020.
 * Must live in {@code io.casehub.clinical.ledger} — never in {@code io.casehub.clinical.entity}.
 *
 * <p>Actor roles:
 * <ul>
 *   <li>{@code "sponsor-notifier"} — successful delivery</li>
 *   <li>{@code "sponsor-notifier-attempt-failed"} — attempt failed, retries remain</li>
 *   <li>{@code "sponsor-notifier-exhausted"} — all attempts consumed</li>
 * </ul>
 */
@Entity
@Table(name = "sponsor_notification_ledger_entry")
@DiscriminatorValue("SponsorNotification")
public class SponsorNotificationLedgerEntry extends LedgerEntry {

    @Column(name = "notification_id", nullable = false)
    public UUID notificationId;

    @Column(name = "deviation_id", nullable = false)
    public UUID deviationId;

    @Column(name = "attempt_number", nullable = false)
    public int attemptNumber;

    @Column(name = "delivered", nullable = false)
    public boolean delivered;

    @Column(name = "failure_reason", length = 1000)
    public String failureReason;
}
