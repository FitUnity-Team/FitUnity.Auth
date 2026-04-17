package com.fitunity.auth.service.oauth;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OAuthStateService {

    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private final Map<String, Instant> issuedStates = new ConcurrentHashMap<>();

    public String issueState() {
        purgeExpired();
        String state = UUID.randomUUID().toString();
        issuedStates.put(state, Instant.now().plus(STATE_TTL));
        return state;
    }

    public boolean consumeIfValid(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }

        purgeExpired();
        Instant expiry = issuedStates.remove(state);
        return expiry != null && expiry.isAfter(Instant.now());
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        issuedStates.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
