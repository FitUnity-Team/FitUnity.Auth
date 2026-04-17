package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserFactoryTest {

    private final UserFactory userFactory = new UserFactory();

    @Test
    void shouldCreateClientUserWithDefaults() {
        Utilisateur user = userFactory.newClientUser("a@b.com", "hash", "Ahmed");

        assertNotNull(user.getId());
        assertEquals("a@b.com", user.getEmail());
        assertEquals("hash", user.getPasswordHash());
        assertEquals("Ahmed", user.getNom());
        assertEquals(Role.CLIENT, user.getRole());
        assertTrue(user.isActive());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
    }

    @Test
    void shouldNormalizeEmailToLowercaseAndTrimmed() {
        Utilisateur user = userFactory.newClientUser("  USER@Example.COM  ", "hash", "Ahmed");

        assertEquals("user@example.com", user.getEmail());
    }

    @Test
    void shouldFallbackToTrimmedEmailWhenNameIsBlank() {
        Utilisateur user = userFactory.newClientUser("  a@b.com  ", "hash", "   ");

        assertEquals("a@b.com", user.getNom());
    }
}
