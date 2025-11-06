package com.selfservice.telegrambot.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PkceStore {
    private static final long TTL_SECONDS = 600; // 10 min

    private static class Entry {
        final String verifier;
        final long expiresAt;
        Entry(String v, long exp) { this.verifier = v; this.expiresAt = exp; }
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    public void put(String nonce, String verifier) {
        long exp = Instant.now().getEpochSecond() + TTL_SECONDS;
        map.put(nonce, new Entry(verifier, exp));
    }

    public String take(String nonce) {
        Entry e = map.remove(nonce);
        if (e == null) return null;
        if (Instant.now().getEpochSecond() > e.expiresAt) return null;
        return e.verifier;
    }
}
