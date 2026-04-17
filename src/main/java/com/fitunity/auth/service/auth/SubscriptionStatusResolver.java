package com.fitunity.auth.service.auth;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SubscriptionStatusResolver {

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NONE";
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(normalized) || "EXPIREE".equals(normalized) || "NONE".equals(normalized)) {
            return normalized;
        }

        return "NONE";
    }
}
