package io.casehub.clinical.entity;

import io.casehub.clinical.api.model.DeviationSeverity;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "protocol_deviation")
public class ProtocolDeviation extends PanacheEntityBase {

    @Id
    public UUID id;

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
}
