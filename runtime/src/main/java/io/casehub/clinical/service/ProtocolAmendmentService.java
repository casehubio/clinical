package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolAmendmentProposedEvent;
import io.casehub.clinical.api.model.AmendmentCaseStatus;
import io.casehub.clinical.api.model.ProtocolAmendmentStatus;
import io.casehub.clinical.entity.ProtocolAmendment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class ProtocolAmendmentService {

    @Inject ProtocolAmendmentLedgerWriter ledgerWriter;
    @Inject Event<ProtocolAmendmentProposedEvent> proposedEvents;

    public ProtocolAmendment propose(UUID trialId, String proposedChange, String tenantId) {
        ProtocolAmendment amendment = persistProposal(trialId, proposedChange, tenantId);
        proposedEvents.fireAsync(new ProtocolAmendmentProposedEvent(
            amendment.id, trialId, proposedChange, tenantId));
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
        amendment.persist();
        ledgerWriter.writeProposalEntry(amendment);
        return amendment;
    }
}
