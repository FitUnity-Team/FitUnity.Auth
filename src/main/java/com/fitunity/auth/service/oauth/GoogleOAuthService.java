package com.fitunity.auth.service.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitunity.auth.service.auth.UserFactory;
import com.fitunity.auth.service.oauth.model.GoogleUserProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GoogleOAuthService {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserFactory userFactory;

    public GoogleOAuthService(
            @Value("${oauth.google.client-id:}") String clientId,
            @Value("${oauth.google.client-secret:}") String clientSecret,
            @Value("${oauth.google.redirect-uri:http://localhost:8081/oauth2/callback/google}") String redirectUri,
            UserFactory userFactory
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.userFactory = userFactory;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .build()
                .toUriString();
    }

    public GoogleUserProfile exchangeCodeForUserProfile(String authorizationCode) {
        requireConfigured();

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("code", authorizationCode);
        requestBody.add("client_id", clientId);
        requestBody.add("client_secret", clientSecret);
        requestBody.add("redirect_uri", redirectUri);
        requestBody.add("grant_type", "authorization_code");

        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                URI.create(GOOGLE_TOKEN_URL),
                HttpMethod.POST,
                new HttpEntity<>(requestBody, tokenHeaders),
                Map.class
        );

        if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
            throw new IllegalArgumentException("token_exchange_failed");
        }

        String idToken = getString(tokenResponse.getBody(), "id_token");
        validateIdTokenClaims(idToken);

        String accessToken = getString(tokenResponse.getBody(), "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("token_exchange_failed");
        }

        HttpHeaders userInfoHeaders = new HttpHeaders();
        userInfoHeaders.setBearerAuth(accessToken);

        ResponseEntity<Map> userInfoResponse = restTemplate.exchange(
                URI.create(GOOGLE_USERINFO_URL),
                HttpMethod.GET,
                new HttpEntity<>(userInfoHeaders),
                Map.class
        );

        if (!userInfoResponse.getStatusCode().is2xxSuccessful() || userInfoResponse.getBody() == null) {
            throw new IllegalArgumentException("userinfo_fetch_failed");
        }

        String email = userFactory.normalizeEmail(getString(userInfoResponse.getBody(), "email"));
        boolean emailVerified = Boolean.parseBoolean(String.valueOf(userInfoResponse.getBody().get("email_verified")));
        String sub = getString(userInfoResponse.getBody(), "sub");
        String name = getString(userInfoResponse.getBody(), "name");

        if (email == null || email.isBlank() || sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("essential_claim_missing");
        }

        return new GoogleUserProfile(sub, email, emailVerified, name);
    }

    private void validateIdTokenClaims(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("token_validation_failed");
        }

        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("token_validation_failed");
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(payloadJson);

            String issuer = payload.path("iss").asText(null);
            if (!("https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer))) {
                throw new IllegalArgumentException("token_validation_failed");
            }

            boolean audienceMatches;
            JsonNode aud = payload.get("aud");
            if (aud == null) {
                audienceMatches = false;
            } else if (aud.isArray()) {
                audienceMatches = false;
                for (JsonNode value : aud) {
                    if (clientId.equals(value.asText())) {
                        audienceMatches = true;
                        break;
                    }
                }
            } else {
                audienceMatches = clientId.equals(aud.asText());
            }

            if (!audienceMatches) {
                throw new IllegalArgumentException("token_validation_failed");
            }

            long exp = payload.path("exp").asLong(0);
            if (exp <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("token_validation_failed");
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) ex;
            }
            throw new IllegalArgumentException("token_validation_failed", ex);
        }
    }

    private void requireConfigured() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("google_oauth_not_configured");
        }
    }

    private String getString(Map payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
