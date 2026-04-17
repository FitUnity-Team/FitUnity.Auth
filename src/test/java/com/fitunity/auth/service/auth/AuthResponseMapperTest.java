package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.dto.AuthResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthResponseMapperTest {

    private final AuthResponseMapper mapper = new AuthResponseMapper();

    @Test
    void shouldMapLoginResponse() {
        Utilisateur user = new Utilisateur();
        user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        user.setEmail("user@test.com");
        user.setRole(Role.COACH);

        AuthResponse response = mapper.toLoginResponse("access-token", user, "ACTIVE");

        assertEquals("access-token", response.getAccessToken());
        assertEquals("11111111-1111-1111-1111-111111111111", response.getUserId());
        assertEquals("user@test.com", response.getEmail());
        assertEquals("COACH", response.getRole());
        assertEquals("ACTIVE", response.getStatutAbonnement());
    }
}
