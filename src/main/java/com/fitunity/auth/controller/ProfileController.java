package com.fitunity.auth.controller;

import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.JwtUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final UtilisateurRepository utilisateurRepository;
    private final JwtUserDetailsService userDetailsService;

    public ProfileController(
            UtilisateurRepository utilisateurRepository,
            JwtUserDetailsService userDetailsService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.userDetailsService = userDetailsService;
    }

    /**
     * GET /profile - Get current user profile
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @AuthenticationPrincipal JwtUserDetailsService.JwtUserDetails userDetails
    ) {
        log.info("Profile request for user: {}", userDetails.getUserId());

        Utilisateur utilisateur = utilisateurRepository.findById(
                UUID.fromString(userDetails.getUserId())
        ).orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", utilisateur.getId().toString());
        profile.put("email", utilisateur.getEmail());
        profile.put("role", utilisateur.getRole().name());
        // Password never returned

        return ResponseEntity.ok(profile);
    }

    /**
     * PUT /profile - Update current user profile
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateProfile(
            @AuthenticationPrincipal JwtUserDetailsService.JwtUserDetails userDetails,
            @RequestBody Map<String, String> updates
    ) {
        log.info("Profile update request for user: {}", userDetails.getUserId());

        Utilisateur utilisateur = utilisateurRepository.findById(
                UUID.fromString(userDetails.getUserId())
        ).orElseThrow(() -> new RuntimeException("User not found"));

        // Only allow email update (password change would require separate endpoint)
        if (updates.containsKey("email")) {
            String newEmail = updates.get("email");
            if (!newEmail.equals(utilisateur.getEmail())) {
                // Check if email already exists
                if (utilisateurRepository.existsByEmail(newEmail)) {
                    throw new RuntimeException("Email already exists");
                }
                utilisateur.setEmail(newEmail);
            }
        }

        utilisateur = utilisateurRepository.save(utilisateur);

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", utilisateur.getId().toString());
        profile.put("email", utilisateur.getEmail());
        profile.put("role", utilisateur.getRole().name());

        return ResponseEntity.ok(profile);
    }
}
