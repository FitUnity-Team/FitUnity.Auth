package com.fitunity.auth.controller;

import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.exception.InvalidCredentialsException;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.auth.SubscriptionStatusResolver;
import com.fitunity.auth.service.auth.UserViewMapper;
import com.fitunity.auth.service.JwtUserDetailsService;
import com.fitunity.auth.service.port.RedisStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final UtilisateurRepository utilisateurRepository;
    private final RedisStore redisStore;
    private final UserViewMapper userViewMapper;
    private final SubscriptionStatusResolver subscriptionStatusResolver;

    public ProfileController(
            UtilisateurRepository utilisateurRepository,
            RedisStore redisStore,
            UserViewMapper userViewMapper,
            SubscriptionStatusResolver subscriptionStatusResolver
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.redisStore = redisStore;
        this.userViewMapper = userViewMapper;
        this.subscriptionStatusResolver = subscriptionStatusResolver;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile(
            @AuthenticationPrincipal JwtUserDetailsService.JwtUserDetails userDetails
    ) {
        Utilisateur utilisateur = utilisateurRepository.findById(UUID.fromString(userDetails.getUserId()))
                .orElseThrow(() -> new InvalidCredentialsException("Utilisateur non trouvé"));
        return ResponseEntity.ok(toProfileResponse(utilisateur));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateProfile(
            @AuthenticationPrincipal JwtUserDetailsService.JwtUserDetails userDetails,
            @RequestBody Map<String, String> updates
    ) {
        Utilisateur utilisateur = utilisateurRepository.findById(UUID.fromString(userDetails.getUserId()))
                .orElseThrow(() -> new InvalidCredentialsException("Utilisateur non trouvé"));

        if (updates.containsKey("nom")) {
            String nom = updates.get("nom");
            if (nom != null && !nom.isBlank()) {
                utilisateur.setNom(nom.trim());
            }
        }

        utilisateur = utilisateurRepository.save(utilisateur);
        return ResponseEntity.ok(toProfileResponse(utilisateur));
    }

    private Map<String, Object> toProfileResponse(Utilisateur utilisateur) {
        String statutAbonnement = resolveSubscriptionStatus(utilisateur.getId().toString());
        return userViewMapper.toUserView(utilisateur, statutAbonnement);
    }

    private String resolveSubscriptionStatus(String userId) {
        try {
            return subscriptionStatusResolver.normalize(redisStore.getSubscriptionStatus(userId));
        } catch (Exception e) {
            return "NONE";
        }
    }
}
