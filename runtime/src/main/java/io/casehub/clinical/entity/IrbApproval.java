package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.IrbDecision;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "irb_approval")
public class IrbApproval extends PanacheEntityBase {

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
