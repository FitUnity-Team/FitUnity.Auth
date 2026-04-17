package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserViewMapperTest {

    private final UserViewMapper mapper = new UserViewMapper();

    @Test
    void shouldMapUserToStablePayloadShape() {
        Utilisateur user = new Utilisateur();
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.setEmail("user@test.com");
        user.setNom("User Name");
        user.setRole(Role.CLIENT);
        user.setActive(true);
        user.setCreatedAt(LocalDateTime.parse("2026-04-17T10:15:30"));

        Map<String, Object> view = mapper.toUserView(user, "NONE");

        assertEquals("11111111-1111-1111-1111-111111111111", view.get("id"));
        assertEquals("user@test.com", view.get("email"));
        assertEquals("User Name", view.get("nom"));
        assertEquals("CLIENT", view.get("role"));
        assertEquals(true, view.get("active"));
        assertEquals(LocalDateTime.parse("2026-04-17T10:15:30"), view.get("dateCreation"));
        assertEquals("NONE", view.get("statutAbonnement"));
    }
}
