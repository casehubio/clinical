package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.api.spi.AmendmentRecommendation;
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
@Table(name = "protocol_amendment")
@NamedQuery(name = "ProtocolAmendment.findByTrialId", query = "SELECT a FROM ProtocolAmendment a WHERE a.trialId = :trialId")
@NamedQuery(name = "ProtocolAmendment.findByIdAndTenantId", query = "SELECT a FROM ProtocolAmendment a WHERE a.id = :id AND a.tenantId = :tenantId")
public class ProtocolAmendment {

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
}
