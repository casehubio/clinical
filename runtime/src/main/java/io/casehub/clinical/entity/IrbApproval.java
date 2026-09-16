package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.IrbDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "irb_approval")
@NamedQuery(name = "IrbApproval.findByIdAndTenantId", query = "SELECT a FROM IrbApproval a WHERE a.id = :id AND a.tenantId = :tenantId")
public class IrbApproval {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "site_id", nullable = false)
    public UUID siteId;

    /**
     * The deviation this IRB approval is for. Nullable for legacy stubs;
     * always set on new rows created by IrbDeviationCaseService.
     * Added in V109.
     */
    @Column(name = "deviation_id")
    public UUID deviationId;

    /**
     * The deviation type this IRB approval covers (e.g. "CONSENT_VIOLATION").
     * Nullable for legacy rows; always set from ProtocolDeviationResolvedEvent on new rows.
     * Used to write CaseMemoryStore IRB domain entries keyed by deviation type.
     * Added in V117.
     */
    @Column(name = "deviation_type")
    public String deviationType;

    @Column(name = "review_type", nullable = false)
    public String reviewType;

    @Column(name = "committee_id", nullable = false)
    public String committeeId;

    @Column(name = "decision_deadline", nullable = false)
    public Instant decisionDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IrbDecision decision = IrbDecision.PENDING;
}
