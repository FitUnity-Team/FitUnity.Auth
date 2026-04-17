package com.fitunity.auth.service.auth;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class UserFactory {

    public Utilisateur newClientUser(String email, String passwordHash, String nom) {
        Utilisateur user = new Utilisateur();
        user.setId(UUID.randomUUID());
        user.setEmail(normalizeEmail(email));
        user.setPasswordHash(passwordHash);
        user.setNom(resolveName(user.getEmail(), nom));
        user.setRole(Role.CLIENT);
        user.setActive(true);

        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }

    private String resolveName(String email, String nom) {
        if (nom == null || nom.isBlank()) {
            return email == null ? null : email.trim();
        }
        return nom.trim();
    }

    public String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
