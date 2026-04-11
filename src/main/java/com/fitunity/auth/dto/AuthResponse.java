package com.fitunity.auth.dto;

import java.util.UUID;

public class AuthResponse {

    private String accessToken;
    private String userId;
    private String email;
    private String role;
    private String statutAbonnement;

    public AuthResponse() {}

    public AuthResponse(String accessToken, String userId, String email, String role, String statutAbonnement) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.statutAbonnement = statutAbonnement;
    }

    // Getters and setters
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatutAbonnement() {
        return statutAbonnement;
    }

    public void setStatutAbonnement(String statutAbonnement) {
        this.statutAbonnement = statutAbonnement;
    }
}
