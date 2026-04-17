package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.dto.AuthResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthResponseMapper {

    public AuthResponse toLoginResponse(String accessToken, Utilisateur user, String statutAbonnement) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setUserId(user.getId().toString());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().getValue());
        response.setStatutAbonnement(statutAbonnement);
        return response;
    }
}
