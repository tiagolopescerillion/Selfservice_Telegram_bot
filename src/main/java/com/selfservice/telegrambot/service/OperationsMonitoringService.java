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

    public record TokenDetails(String state, String token) {
        public static TokenDetails none() {
            return new TokenDetails("NONE", null);
        }
    }

    public record SessionSnapshot(
            String channel,
            String sessionId,
            boolean loggedIn,
            boolean optIn,
            String username,
            Instant startedAt,
            Instant lastSeen,
            TokenDetails token
    ) {
        public SessionSnapshot update(boolean loggedInUpdate, boolean optInUpdate, String newUsername,
                                      TokenDetails updatedToken, Instant now) {
            String mergedUsername = (newUsername != null && !newUsername.isBlank()) ? newUsername.strip() : username;
            TokenDetails mergedToken = updatedToken != null ? updatedToken : token;
            return new SessionSnapshot(channel, sessionId, loggedInUpdate, optInUpdate, mergedUsername, startedAt, now,
                    mergedToken);
        }

        public SessionSnapshot logout(Instant now) {
            return new SessionSnapshot(channel, sessionId, false, optIn, username, startedAt, now, token);
        }
    }

    private static final int HISTORY_LIMIT = 200;

    private final Map<String, SessionSnapshot> activeSessions = new ConcurrentHashMap<>();
    private final Deque<SessionSnapshot> sessionHistory = new ConcurrentLinkedDeque<>();

    public void recordActivity(String channel, String sessionId, String username, boolean loggedIn, boolean optIn) {
        recordActivity(channel, sessionId, username, loggedIn, null, optIn);
    }

    public void recordActivity(String channel, String sessionId, String username, boolean loggedIn, TokenDetails tokenDetails,
            boolean optIn) {
        if (channel == null || sessionId == null) {
            return;
        }

        Instant now = Instant.now();
        String key = key(channel, sessionId);
        activeSessions.compute(key, (k, existing) -> {
            if (existing == null) {
                return new SessionSnapshot(channel, sessionId, loggedIn, optIn, normalize(username), now, now,
                        tokenDetails != null ? tokenDetails : TokenDetails.none());
            }
            return existing.update(loggedIn, optIn, normalize(username), tokenDetails, now);
        });
    }

    public TokenDetails toTokenDetails(UserSessionService.TokenSnapshot snapshot) {
        if (snapshot == null) {
            return TokenDetails.none();
        }
        String state = snapshot.state() != null ? snapshot.state().name() : "NONE";
        return new TokenDetails(state, snapshot.token());
    }

    public TokenDetails toTokenDetails(com.selfservice.whatsapp.service.WhatsappSessionService.TokenSnapshot snapshot) {
        if (snapshot == null) {
            return TokenDetails.none();
        }
        String state = snapshot.state() != null ? snapshot.state().name() : "NONE";
        return new TokenDetails(state, snapshot.token());
    }

    public void markLoggedIn(String channel, String sessionId, String username) {
        recordActivity(channel, sessionId, username, true, false);
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
