package com.selfservice.telegrambot.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {

    public static final class TokenInfo {
        public final String accessToken;
        public final long   expiryEpochMs;
        public TokenInfo(String accessToken, long expiryEpochMs) {
            this.accessToken = accessToken;
            this.expiryEpochMs = expiryEpochMs;
        }
    }

    private final Map<Long, TokenInfo> byChat = new ConcurrentHashMap<>();

    public void save(long chatId, String accessToken, long expiresInSeconds) {
        long exp = System.currentTimeMillis() + expiresInSeconds * 1000L;
        byChat.put(chatId, new TokenInfo(accessToken, exp));
    }

    public String getValidAccessToken(long chatId) {
        TokenInfo ti = byChat.get(chatId);
        if (ti == null) return null;
        // 30s safety buffer
        if (ti.expiryEpochMs <= System.currentTimeMillis() + 30_000) {
            byChat.remove(chatId);
            return null;
        }
        return ti.accessToken;
    }
}
