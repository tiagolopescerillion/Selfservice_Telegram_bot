package com.selfservice.telegrambot.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class OperationsMonitoringService {

    public record SessionSnapshot(
            String channel,
            String sessionId,
            boolean loggedIn,
            String username,
            Instant startedAt,
            Instant lastSeen
    ) {
        public SessionSnapshot update(boolean loggedInUpdate, String newUsername, Instant now) {
            String mergedUsername = (newUsername != null && !newUsername.isBlank()) ? newUsername.strip() : username;
            return new SessionSnapshot(channel, sessionId, loggedInUpdate, mergedUsername, startedAt, now);
        }

        public SessionSnapshot logout(Instant now) {
            return new SessionSnapshot(channel, sessionId, false, username, startedAt, now);
        }
    }

    private static final int HISTORY_LIMIT = 200;

    private final Map<String, SessionSnapshot> activeSessions = new ConcurrentHashMap<>();
    private final Deque<SessionSnapshot> sessionHistory = new ConcurrentLinkedDeque<>();

    public void recordActivity(String channel, String sessionId, String username, boolean loggedIn) {
        if (channel == null || sessionId == null) {
            return;
        }

        Instant now = Instant.now();
        String key = key(channel, sessionId);
        activeSessions.compute(key, (k, existing) -> {
            if (existing == null) {
                return new SessionSnapshot(channel, sessionId, loggedIn, normalize(username), now, now);
            }
            return existing.update(loggedIn, normalize(username), now);
        });
    }

    public void markLoggedIn(String channel, String sessionId, String username) {
        recordActivity(channel, sessionId, username, true);
    }

    public void markLoggedOut(String channel, String sessionId) {
        if (channel == null || sessionId == null) {
            return;
        }

        String key = key(channel, sessionId);
        SessionSnapshot ended = activeSessions.remove(key);
        Instant now = Instant.now();
        if (ended != null) {
            SessionSnapshot completed = ended.logout(now);
            sessionHistory.addFirst(completed);
            trimHistory();
        }
    }

    public List<SessionSnapshot> getActiveSessions() {
        return activeSessions.values().stream()
                .sorted(Comparator.comparing(SessionSnapshot::lastSeen).reversed())
                .toList();
    }

    public List<SessionSnapshot> getRecentSessions() {
        return sessionHistory.stream().limit(HISTORY_LIMIT).toList();
    }

    private void trimHistory() {
        while (sessionHistory.size() > HISTORY_LIMIT) {
            sessionHistory.removeLast();
        }
    }

    private String normalize(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String key(String channel, String sessionId) {
        return channel + "::" + sessionId;
    }
}
