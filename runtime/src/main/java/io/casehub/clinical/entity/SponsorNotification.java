package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.api.model.SponsorNotificationStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable sponsor notification entity. Tracks delivery state across retry cycles.
 *
 * <p>All fields from {@code SponsorNotificationRequest} are snapshotted at creation
 * time so retries do not re-resolve PI identity or connector config.
 *
 * <p>Invariant: this entity must never gain {@code @OneToMany}, {@code @ManyToOne},
 * or any other association mapping. {@code SponsorNotificationStore.load()} reads
 * the entity in a short transaction that closes before the connector call (Phase 2);
 * any lazy-loaded association would throw {@code LazyInitializationException} in Phase 2.
 *
 * <p>On the default datasource. Migration V115.
 */
@Entity
@Table(
        name = "sponsor_notification",
        indexes = @Index(name = "idx_sn_eligible", columnList = "status, next_retry_after")
)
public class SponsorNotification extends PanacheEntityBase {

    @Id
    @Column(nullable = false)
    public UUID id;

    // Subject references — no FK constraints (cross-datasource references by convention)
    @Column(name = "deviation_id", nullable = false)
    public UUID deviationId;

    @Column(name = "trial_id", nullable = false)
    public UUID trialId;

    @Column(name = "site_id", nullable = false)
    public UUID siteId;

    // Future multi-tenancy — nullable; findEligibleIds() must filter by tenantId when non-null
    @Column(name = "tenant_id", length = 64)
    public String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public SponsorNotificationStatus status;

    /** Number of completed delivery attempts. Phase 3 sets this to attemptNumber. */
    @Column(nullable = false)
    public int attempts;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_attempted_at")
    public Instant lastAttemptedAt;

    /** Null = eligible immediately. Set to now + retryInterval on each failure. */
    @Column(name = "next_retry_after")
    public Instant nextRetryAfter;

    @Column(name = "delivered_at")
    public Instant deliveredAt;

    @Column(name = "failure_reason", length = 1000)
    public String failureReason;

    // Payload snapshot — resolved at notify() time, used verbatim on retry
    @Column(name = "pi_id", length = 255)
    public String piId;

    @Column(name = "pi_display_name", length = 255)
    public String piDisplayName;

    @Column(name = "connector_id", nullable = false, length = 128)
    public String connectorId;

    @Column(name = "destination", nullable = false, length = 2048)
    public String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public DeviationSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_status", nullable = false, length = 32)
    public PiApprovalStatus terminalStatus;

    @Column(name = "deviation_type", nullable = false, length = 128)
    public String deviationType;
}
