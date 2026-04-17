package com.fitunity.auth.service.oauth.model;

public class GoogleUserProfile {

    private final String subject;
    private final String email;
    private final boolean emailVerified;
    private final String displayName;

    public GoogleUserProfile(String subject, String email, boolean emailVerified, String displayName) {
        this.subject = subject;
        this.email = email;
        this.emailVerified = emailVerified;
        this.displayName = displayName;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getDisplayName() {
        return displayName;
    }
}
