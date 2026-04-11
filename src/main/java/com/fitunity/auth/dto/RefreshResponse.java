package com.fitunity.auth.dto;

public class RefreshResponse {

    private String accessToken;

    public RefreshResponse() {}

    public RefreshResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
