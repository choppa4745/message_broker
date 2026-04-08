package com.pigeonmq.service;

import com.pigeonmq.domain.ClientSession;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final ConcurrentMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    public ClientSession register(String clientId, Channel channel) {
        ClientSession existing = sessions.get(clientId);
        if (existing != null && existing.isActive()) {
            log.warn("Replacing active session for client {}", clientId);
            existing.getChannel().close();
        }
        ClientSession session = new ClientSession(clientId, channel);
        sessions.put(clientId, session);
        log.info("Client connected: {}", clientId);
        return session;
    }

    public void remove(String clientId) {
        ClientSession removed = sessions.remove(clientId);
        if (removed != null) {
            log.info("Client disconnected: {}", clientId);
        }
    }

    public ClientSession get(String clientId) {
        return sessions.get(clientId);
    }

    public List<ClientSession> getActiveSessions() {
        return sessions.values().stream()
                .filter(ClientSession::isActive)
                .toList();
    }

    public int getActiveCount() {
        return (int) sessions.values().stream().filter(ClientSession::isActive).count();
    }

    public ClientSession findByChannel(Channel channel) {
        return sessions.values().stream()
                .filter(s -> s.getChannel().equals(channel))
                .findFirst()
                .orElse(null);
    }
}
