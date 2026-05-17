package io.casehub.clinical.service;

import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.api.gateway.InboundNormaliser;
import io.casehub.qhorus.api.gateway.NormalisedMessage;
import io.casehub.qhorus.api.message.MessageType;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClinicalInboundNormaliser implements InboundNormaliser {

    @Override
    public NormalisedMessage normalise(ChannelRef channel, InboundHumanMessage raw) {
        MessageType type = detectType(raw.content());
        return new NormalisedMessage(type, raw.content(), "human:" + raw.externalSenderId());
    }

    private MessageType detectType(String content) {
        if (content == null) return MessageType.QUERY;
        if (content.contains("\"decision\":\"APPROVED\"")) return MessageType.DONE;
        if (content.contains("\"decision\":\"REJECTED\"")) return MessageType.DECLINE;
        return MessageType.QUERY;
    }
}
