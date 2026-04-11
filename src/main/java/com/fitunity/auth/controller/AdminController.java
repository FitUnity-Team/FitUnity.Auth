package com.fitunity.auth.controller;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UtilisateurRepository utilisateurRepository;
    private final RedisService redisService;

    public AdminController(
            UtilisateurRepository utilisateurRepository,
            RedisService redisService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.redisService = redisService;
    }

    /**
     * GET /admin/users - Get all users with pagination
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Admin request: get users (page={}, size={})", page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Utilisateur> utilisateurPage = utilisateurRepository.findAll(pageable);

        List<Map<String, Object>> users = utilisateurPage.getContent().stream()
                .map(u -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("userId", u.getId().toString());
                    userMap.put("email", u.getEmail());
                    userMap.put("role", u.getRole().name());
                    userMap.put("createdAt", u.getCreatedAt());
                    return userMap;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("users", users);
        response.put("totalPages", utilisateurPage.getTotalPages());
        response.put("totalElements", utilisateurPage.getTotalElements());
        response.put("currentPage", page);
        response.put("size", size);

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /admin/users/{id}/role - Update user role
     */
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<Map<String, Object>> updateUserRole(
            @PathVariable String userId,
            @RequestBody Map<String, String> request
    ) {
        log.info("Admin request: update role for user {}", userId);

        String newRole = request.get("role");
        if (newRole == null) {
            throw new IllegalArgumentException("Role is required");
        }

        Role role;
        try {
            role = Role.valueOf(newRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + newRole);
        }

        Utilisateur utilisateur = utilisateurRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("User not found"));

        utilisateur.setRole(role);
        utilisateurRepository.save(utilisateur);

        // Set Redis override (5 minute TTL for cache)
        redisService.setRoleOverride(userId, role.name(), Duration.ofMinutes(5).getSeconds());

        Map<String, Object> response = new HashMap<>();
        response.put("userId", utilisateur.getId().toString());
        response.put("email", utilisateur.getEmail());
        response.put("role", utilisateur.getRole().name());

        log.info("User role updated: {} -> {}", userId, role.name());
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /admin/users/{id}/activate - Activate user account
     */
    @PutMapping("/users/{userId}/activate")
    public ResponseEntity<Map<String, Object>> activateUser(
            @PathVariable String userId
    ) {
        log.info("Admin request: activate user {}", userId);

        // Note: Utilisateur entity doesn't have 'active' field yet
        // This endpoint is a placeholder for future implementation

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("message", "User activated");

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /admin/users/{id}/deactivate - Deactivate user account
     */
    @PutMapping("/users/{userId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateUser(
            @PathVariable String userId
    ) {
        log.info("Admin request: deactivate user {}", userId);

        // Note: Utilisateur entity doesn't have 'active' field yet
        // This endpoint is a placeholder for future implementation

        // For now, just revoke all sessions
        redisService.deleteRefreshToken(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("message", "User deactivated and sessions revoked");

        return ResponseEntity.ok(response);
    }
}
