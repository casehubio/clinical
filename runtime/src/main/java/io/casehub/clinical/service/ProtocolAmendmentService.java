package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolAmendmentProposedEvent;
import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.entity.ProtocolAmendment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class ProtocolAmendmentService {

    @Inject ProtocolAmendmentLedgerWriter ledgerWriter;
    @Inject Event<ProtocolAmendmentProposedEvent> proposedEvents;
    @Inject TransactionSynchronizationRegistry txSync;
    @Inject
            EntityManager                         em;


    @Transactional
    public ProtocolAmendment propose(UUID trialId, String proposedChange, String tenantId) {
        ProtocolAmendment amendment = persistProposal(trialId, proposedChange, tenantId);
        var event = new ProtocolAmendmentProposedEvent(amendment.id, trialId, proposedChange, tenantId);
        txSync.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {}

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) proposedEvents.fireAsync(event);
            }
        });
        return amendment;
    }

    @Transactional
    ProtocolAmendment persistProposal(UUID trialId, String proposedChange, String tenantId) {
        ProtocolAmendment amendment = new ProtocolAmendment();
        amendment.id = UUID.randomUUID();
        amendment.trialId = trialId;
        amendment.proposedChange = proposedChange;
        amendment.tenantId = tenantId;
        amendment.status = ProtocolAmendmentStatus.PROPOSED;
        amendment.amendmentCaseStatus = AmendmentCaseStatus.NONE;
        amendment.proposedAt = Instant.now();
        em.persist(amendment);
        ledgerWriter.writeProposalEntry(amendment);
        return amendment;
    }
}
