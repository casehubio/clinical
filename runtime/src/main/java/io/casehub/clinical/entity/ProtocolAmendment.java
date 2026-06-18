package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "protocol_amendment")
public class ProtocolAmendment extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "trial_id", nullable = false)
    public UUID trialId;

    @Column(name = "proposed_change", nullable = false, columnDefinition = "TEXT")
    public String proposedChange;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public ProtocolAmendmentStatus status = ProtocolAmendmentStatus.PROPOSED;

    @Enumerated(EnumType.STRING)
    @Column(name = "amendment_case_status", nullable = false)
    public AmendmentCaseStatus amendmentCaseStatus = AmendmentCaseStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "supervisor_recommendation")
    public AmendmentRecommendation supervisorRecommendation;

    @Column(name = "engine_case_id")
    public UUID engineCaseId;

    @Column(name = "proposed_at", nullable = false)
    public Instant proposedAt;

    public static List<ProtocolAmendment> findByTrialId(UUID trialId) {
        return list("trialId", trialId);
    }

    public static ProtocolAmendment findByIdForTenant(UUID id, CurrentPrincipal principal) {
        if (principal.isCrossTenantAdmin()) return findById(id);
        return find("id = ?1 and tenantId = ?2", id, principal.tenancyId()).firstResult();
    }
}
