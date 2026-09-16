package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.TrialPhase;
import io.casehub.clinical.api.model.TrialStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "clinical_trial")
@NamedQuery(name = "ClinicalTrial.findByIdAndTenantId", query = "SELECT t FROM ClinicalTrial t WHERE t.id = :id AND t.tenantId = :tenantId")
public class ClinicalTrial {

    @Id
    public UUID id;

    @Column(name = "protocol_id", nullable = false)
    public String protocolId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TrialPhase phase;

    @Column(nullable = false)
    public String sponsor;

    @Column(name = "target_enrollment", nullable = false)
    public int targetEnrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TrialStatus status = TrialStatus.PLANNING;

    @Column(name = "sponsor_notification_connector_id")
    public String sponsorNotificationConnectorId;

    @Column(name = "sponsor_notification_destination")
    public String sponsorNotificationDestination;

    @Column(name = "tenant_id", nullable = false)
    public String tenantId = "default";

    @Column(name = "safety_officer_connector_id")
    public String safetyOfficerConnectorId;

    @Column(name = "safety_officer_destination", length = 2048)
    public String safetyOfficerDestination;

    /** Engine case ID — set when trial transitions to ACTIVE; null until then. */
    @Column(name = "engine_case_id")
    public UUID engineCaseId;
}
