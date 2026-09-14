package io.casehub.clinical.push;

import io.casehub.pages.push.SessionSender;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ClinicalSessionSender implements SessionSender {

    private static final Logger LOG = Logger.getLogger(ClinicalSessionSender.class);

    @Inject ConnectionRegistry registry;

    @Override
    public void send(String connectionId, String payload) {
        WebSocketConnection conn = registry.get(connectionId);
        if (conn == null || !conn.isOpen()) {
            LOG.debugf("Connection %s not found or closed — skipping send", connectionId);
            return;
        }
        try {
            conn.sendTextAndAwait(payload);
        } catch (Exception e) {
            LOG.debugf(e, "Send failed for connection %s — connection may have closed", connectionId);
        }
    }
}
