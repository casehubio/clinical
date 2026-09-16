package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
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
@Table(name = "protocol_deviation")
@NamedQuery(name = "ProtocolDeviation.findByIdAndTenantId", query = "SELECT d FROM ProtocolDeviation d WHERE d.id = :id AND d.tenantId = :tenantId")
public class ProtocolDeviation {

    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "site_id", nullable = false)
    public UUID siteId;

    @Column(name = "deviation_type", nullable = false)
    public String deviationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public DeviationSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "pi_approval_status", nullable = false)
    public PiApprovalStatus piApprovalStatus = PiApprovalStatus.PENDING;

    @Column(name = "pi_command_channel_name")
    public String piCommandChannelName;

    @Column(name = "commanded_at")
    public Instant commandedAt;

    @Column(name = "response_deadline")
    public Instant responseDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_requirement")
    public EscalationRequirement escalationRequirement;

    /** Links this CRITICAL deviation to its IRB review engine case. Null until IrbDeviationCaseService starts the case. */
    @Column(name = "engine_case_id")
    public UUID engineCaseId;
}
