package io.casehub.clinical.service;

import io.casehub.clinical.api.ProtocolDeviationResolvedEvent;
import io.casehub.clinical.api.model.EscalationRequirement;
import io.casehub.clinical.api.model.PiApprovalStatus;
import io.casehub.clinical.entity.ProtocolDeviation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class PiResponseListener implements MessageObserver {


    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(PiResponseListener.class);
    private static final Pattern CHANNEL_PATTERN =
        Pattern.compile("clinical/deviation/dev-([0-9a-f-]+)/pi-oversight");

    @Inject Event<ProtocolDeviationResolvedEvent> resolvedEvent;
    @Inject DeviationLedgerWriter ledgerWriter;
    @Inject io.casehub.clinical.memory.ClinicalMemoryService memoryService;

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void onMessage(MessageReceivedEvent event) {
        process(event.channelName(), event.messageType(), event.senderId());
    }

    @Transactional
    public void process(String channelName, MessageType messageType, String senderId) {
        if (messageType != MessageType.DONE && messageType != MessageType.DECLINE) {return;}

        Matcher matcher = CHANNEL_PATTERN.matcher(channelName);
        if (!matcher.matches()) {return;}

        UUID              deviationId = UUID.fromString(matcher.group(1));
        ProtocolDeviation deviation   = ProtocolDeviation.findById(deviationId);
        if (deviation == null) {return;}
        if (deviation.piApprovalStatus != PiApprovalStatus.COMMANDED) {return;}

        boolean approved = messageType == MessageType.DONE;

        if (approved) {
            boolean needsEscalation = deviation.escalationRequirement != null
                                      && deviation.escalationRequirement != EscalationRequirement.NONE;
            deviation.piApprovalStatus = needsEscalation
                                         ? PiApprovalStatus.ESCALATED : PiApprovalStatus.APPROVED;
        } else {
            deviation.piApprovalStatus = PiApprovalStatus.REJECTED;
        }

        ledgerWriter.writeResolutionEntry(deviation, deviation.piApprovalStatus,
                                          senderId, ActorType.HUMAN, "pi-authoriser");
        try {
            memoryService.storePiDecision(deviation.id, deviation.siteId,
                                          deviation.deviationType, deviation.piApprovalStatus, deviation.tenantId);
        } catch (Exception e) {
            LOG.warnf("PI decision memory store failed (non-fatal): %s", e.getMessage());
        }

        resolvedEvent.fireAsync(new ProtocolDeviationResolvedEvent(
                deviation.id, deviation.siteId, deviation.severity,
                deviation.escalationRequirement != null
                ? deviation.escalationRequirement : EscalationRequirement.NONE,
                deviation.piApprovalStatus,
                deviation.deviationType,
                senderId,
                deviation.tenantId
        ));}
}
