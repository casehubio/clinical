package io.casehub.clinical.push;

import io.casehub.pages.push.EventStore;
import io.casehub.pages.push.PushRequest;
import io.casehub.pages.push.StoredEvent;
import io.casehub.pages.push.TopicRegistry;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@WebSocket(path = "/ws/push")
public class ClinicalPushEndpoint {

    private static final Logger LOG = Logger.getLogger(ClinicalPushEndpoint.class);

    @Inject ConnectionRegistry registry;
    @Inject TopicRegistry topicRegistry;
    @Inject EventStore eventStore;
    @Inject ClinicalSessionSender sender;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        registry.register(connection);
        LOG.debugf("WebSocket connected: %s", connection.id());
    }

    @OnTextMessage
    public void onMessage(WebSocketConnection connection, String message) {
        try {
            PushRequest request = PushRequest.parse(message);
            if (request instanceof PushRequest.Listen listen) {
                handleListen(connection.id(), listen);
            } else if (request instanceof PushRequest.Unlisten unlisten) {
                handleUnlisten(connection.id(), unlisten);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to handle push message from %s", connection.id());
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "unknown error";
            sender.send(connection.id(), "{\"op\":\"error\",\"message\":\"" + msg + "\"}");
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        topicRegistry.removeConnection(connection.id());
        registry.remove(connection.id());
        LOG.debugf("WebSocket disconnected: %s", connection.id());
    }

    private void handleListen(String connectionId, PushRequest.Listen listen) {
        topicRegistry.listen(connectionId, listen.topics());

        Map<String, Long> since = listen.since();
        if (since != null) {
            for (var entry : since.entrySet()) {
                List<StoredEvent> missed = eventStore.replay(entry.getKey(), entry.getValue(), 100);
                for (StoredEvent stored : missed) {
                    String wireMsg = "{\"op\":\"event\",\"topic\":\"" + stored.topic() +
                        "\",\"seq\":" + stored.seq() +
                        ",\"payload\":" + stored.payloadJson() + "}";
                    sender.send(connectionId, wireMsg);
                }
            }
        }

        String topicsJson = listen.topics().stream()
            .map(t -> "\"" + t + "\"")
            .collect(Collectors.joining(",", "[", "]"));
        sender.send(connectionId, "{\"op\":\"ack\",\"id\":\"" + listen.id() + "\",\"topics\":" + topicsJson + "}");
    }

    private void handleUnlisten(String connectionId, PushRequest.Unlisten unlisten) {
        topicRegistry.unlisten(connectionId, unlisten.topics());
        sender.send(connectionId, "{\"op\":\"ack\",\"id\":\"" + unlisten.id() + "\"}");
    }
}
