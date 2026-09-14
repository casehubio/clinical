package io.casehub.clinical.push;

import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ConnectionRegistry {

    private final ConcurrentHashMap<String, WebSocketConnection> connections = new ConcurrentHashMap<>();

    public void register(WebSocketConnection connection) {
        connections.put(connection.id(), connection);
    }

    public void remove(String connectionId) {
        connections.remove(connectionId);
    }

    public WebSocketConnection get(String connectionId) {
        return connections.get(connectionId);
    }
}
