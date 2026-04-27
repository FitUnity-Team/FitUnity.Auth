package com.fitunity.auth.domain;

public enum Role {
    CLIENT("CLIENT"),
    COACH("COACH"),
    SUB_ADMIN("SUB_ADMIN"),
    SUB_ADMIN_ESTORE("SUB_ADMIN-estore"),
    SUB_ADMIN_TRAINING("SUB_ADMIN-training"),
    ADMIN("ADMIN");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Role fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }

        String normalized = raw.trim();
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(normalized) || role.value.equalsIgnoreCase(normalized)) {
                return role;
            }
        }

        throw new IllegalArgumentException("Unknown role: " + raw);
    }
}
