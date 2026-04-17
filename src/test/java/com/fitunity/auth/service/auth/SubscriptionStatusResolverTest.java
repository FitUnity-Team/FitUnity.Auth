package com.fitunity.auth.service.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubscriptionStatusResolverTest {

    private final SubscriptionStatusResolver resolver = new SubscriptionStatusResolver();

    @Test
    void normalizeShouldFallbackToNoneForInvalidStatus() {
        assertEquals("NONE", resolver.normalize("unexpected"));
    }

    @Test
    void normalizeShouldFallbackToNoneForNullOrBlank() {
        assertEquals("NONE", resolver.normalize(null));
        assertEquals("NONE", resolver.normalize("   "));
    }

    @Test
    void normalizeShouldReturnUppercaseForKnownValues() {
        assertEquals("ACTIVE", resolver.normalize("active"));
        assertEquals("EXPIREE", resolver.normalize("expiree"));
        assertEquals("NONE", resolver.normalize("none"));
    }
}
