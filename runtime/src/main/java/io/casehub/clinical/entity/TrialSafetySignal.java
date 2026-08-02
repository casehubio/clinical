package io.casehub.clinical.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trial_safety_signal")
public class TrialSafetySignal extends PanacheEntityBase {

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

    public static TrialSafetySignal findByTrialAndType(UUID trialId, String signalType, String tenantId) {
        return find("trialId = ?1 AND signalType = ?2 AND tenantId = ?3", trialId, signalType, tenantId).firstResult();
    }

    public static List<TrialSafetySignal> findActiveByTrial(UUID trialId, String tenantId) {
        return list("trialId = ?1 AND tenantId = ?2 AND resolvedAt IS NULL", trialId, tenantId);
    }
}
