package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.SiteStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "trial_site")
@NamedQuery(name = "TrialSite.findByIdAndTenantId", query = "SELECT s FROM TrialSite s WHERE s.id = :id AND s.tenantId = :tenantId")
public class TrialSite {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "trial_id", nullable = false)
    public UUID trialId;

    @Column(name = "investigator_id", nullable = false)
    public String investigatorId;

    @Column(name = "target_enrollment", nullable = false)
    public int targetEnrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public SiteStatus status = SiteStatus.PENDING;
}
