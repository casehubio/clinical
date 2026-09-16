package io.casehub.clinical.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trial_safety_signal")
@NamedQuery(name = "TrialSafetySignal.findByTrialAndType", query = "SELECT s FROM TrialSafetySignal s WHERE s.trialId = :trialId AND s.signalType = :signalType AND s.tenantId = :tenantId")
@NamedQuery(name = "TrialSafetySignal.findActiveByTrial", query = "SELECT s FROM TrialSafetySignal s WHERE s.trialId = :trialId AND s.tenantId = :tenantId AND s.resolvedAt IS NULL")
public class TrialSafetySignal {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId;

    @Column(name = "trial_id", nullable = false)
    public UUID trialId;

    @Column(name = "signal_type", nullable = false, length = 50)
    public String signalType;

    @Column(name = "affected_site_count", nullable = false)
    public int affectedSiteCount;

    @Column(length = 2048)
    public String summary;

    @Column(name = "first_detected_at", nullable = false)
    public Instant firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    public Instant lastDetectedAt;

    @Column(name = "resolved_at")
    public Instant resolvedAt;
    @Column(name = "work_item_id")
    public UUID    workItemId;

}
