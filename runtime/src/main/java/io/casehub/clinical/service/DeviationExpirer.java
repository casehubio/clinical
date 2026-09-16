package io.casehub.clinical.service;

import io.casehub.clinical.api.ClinicalActors;
import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.runtime.message.CommitmentService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles per-deviation expiration with independent transaction isolation.
 *
 * <p>{@link #findOverdueIds()} reads all COMMANDED deviations past their deadline in a
 * short REQUIRED transaction. {@link #expireOne(UUID)} processes each deviation in a
 * dedicated REQUIRES_NEW transaction — if that deviation's writes fail, only its
 * sub-transaction rolls back; other already-committed expirations are unaffected.
 */
@ApplicationScoped
public class DeviationExpirer {

    @Inject CommitmentService commitmentService;
    @Inject Event<ProtocolDeviationResolvedEvent> resolvedEvent;
    @Inject DeviationLedgerWriter ledgerWriter;
    @Inject io.casehub.clinical.memory.ClinicalMemoryService memoryService;
    @Inject
            EntityManager                                    em;


    @Transactional
    public List<UUID> findOverdueIds() {
        return em.createQuery("SELECT d FROM ProtocolDeviation d WHERE d.piApprovalStatus = :status AND d.responseDeadline < :now", ProtocolDeviation.class)
            .setParameter("status", PiApprovalStatus.COMMANDED).setParameter("now", Instant.now())
            .getResultList()
            .stream().map(d -> d.id).toList();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void expireOne(UUID deviationId) {
        ProtocolDeviation d = em.find(ProtocolDeviation.class, deviationId);
        if (d == null || d.piApprovalStatus != PiApprovalStatus.COMMANDED) return;

        d.piApprovalStatus = PiApprovalStatus.EXPIRED;
        commitmentService.fail(d.id.toString());
        memoryService.storePiDecision(d.id, d.siteId, d.deviationType, PiApprovalStatus.EXPIRED, d.tenantId);
        ledgerWriter.writeResolutionEntry(d, PiApprovalStatus.EXPIRED,
            ClinicalActors.CLINICAL_SERVICE, ActorType.SYSTEM, "deviation-expiration-job");
        // Fire-and-forget: observer failures are asynchronous and not surfaced here.
        // The deviation is already EXPIRED and the Commitment is already FAILED at this point.
        // When qhorus#153 lands and @ObservesAsync consumers are wired, ensure downstream
        // observers handle their own failure semantics — this method will not be retried.
        resolvedEvent.fireAsync(new ProtocolDeviationResolvedEvent(
            d.id, d.siteId, d.severity,
            d.escalationRequirement != null ? d.escalationRequirement : EscalationRequirement.NONE,
            PiApprovalStatus.EXPIRED,
            d.deviationType,
            null,
            d.tenantId
        ));
    }
}
