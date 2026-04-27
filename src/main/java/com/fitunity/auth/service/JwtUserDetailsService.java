package com.fitunity.auth.service;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.port.IdentityUserDetails;
import com.fitunity.auth.service.port.RedisStore;
import com.fitunity.auth.service.port.UserIdentityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

@Service
public class JwtUserDetailsService implements UserIdentityService {

    private static final Logger log = LoggerFactory.getLogger(JwtUserDetailsService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final RedisStore redisStore;

    public JwtUserDetailsService(UtilisateurRepository utilisateurRepository, RedisStore redisStore) {
        this.utilisateurRepository = utilisateurRepository;
        this.redisStore = redisStore;
    }

    @Override
    public UserDetails loadUserByUserId(String userId) {
        try {
            Utilisateur utilisateur = utilisateurRepository.findById(java.util.UUID.fromString(userId)).orElse(null);

            if (utilisateur == null) {
                log.warn("User not found with id: {}", userId);
                return null;
            }

            // Check for role override in Redis (for admin role changes)
            String roleOverride = redisStore.getRoleOverrideFromRedis(userId);
            Role effectiveRole = roleOverride != null
                    ? Role.fromValue(roleOverride)
                    : utilisateur.getRole();

            if (!utilisateur.isActive()) {
                log.warn("Inactive user cannot be loaded for authentication: {}", userId);
                return null;
            }

            return new JwtUserDetails(
                    utilisateur.getId().toString(),
                    utilisateur.getEmail(),
                    effectiveRole
            );
        } catch (Exception e) {
            log.error("Error loading user by userId: {}", userId, e);
            return null;
        }
    }

    /**
     * Internal class representing user details for Spring Security.
     * Does not include password - used for JWT-authenticated requests.
     */
    public static class JwtUserDetails implements IdentityUserDetails {

        private final String userId;
        private final String email;
        private final Role role;

        JwtUserDetails(String userId, String email, Role role) {
            this.userId = userId;
            this.email = email;
            this.role = role;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
        }

        @Override
        public String getPassword() {
            return null; // Password not needed for JWT-authenticated requests
        }

        @Override
        public String getUsername() {
            return email;
        }

        public String getUserId() {
            return userId;
        }

        public Role getRole() {
            return role;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
