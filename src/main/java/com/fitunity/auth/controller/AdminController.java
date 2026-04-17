package com.fitunity.auth.controller;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.repository.UtilisateurRepository;
import com.fitunity.auth.service.auth.SubscriptionStatusResolver;
import com.fitunity.auth.service.auth.UserViewMapper;
import com.fitunity.auth.service.port.AuthFacade;
import com.fitunity.auth.service.port.RedisStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UtilisateurRepository utilisateurRepository;
    private final AuthFacade authFacade;
    private final RedisStore redisStore;
    private final UserViewMapper userViewMapper;
    private final SubscriptionStatusResolver subscriptionStatusResolver;

    public AdminController(
            UtilisateurRepository utilisateurRepository,
            AuthFacade authFacade,
            RedisStore redisStore,
            UserViewMapper userViewMapper,
            SubscriptionStatusResolver subscriptionStatusResolver
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.authFacade = authFacade;
        this.redisStore = redisStore;
        this.userViewMapper = userViewMapper;
        this.subscriptionStatusResolver = subscriptionStatusResolver;
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int boundedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(page, boundedSize);
        Page<Utilisateur> result = utilisateurRepository.findAll(pageable);

        List<Map<String, Object>> content = result.getContent().stream().map(this::toUserResponse).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("pageable", Map.of("pageNumber", page, "pageSize", boundedSize));
        response.put("totalElements", result.getTotalElements());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<Map<String, Object>> updateRole(@PathVariable("id") String userId, @RequestBody Map<String, String> body) {
        String roleText = body.get("role");
        if (roleText == null || roleText.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }

        Role role = Role.fromValue(roleText);
        Utilisateur updated = authFacade.updateRole(userId, role);
        return ResponseEntity.ok(toUserResponse(updated));
    }

    @PutMapping("/users/{id}/activate")
    public ResponseEntity<Map<String, Object>> activate(@PathVariable("id") String userId) {
        Utilisateur updated = authFacade.activateUser(userId);
        return ResponseEntity.ok(toUserResponse(updated));
    }

    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivate(@PathVariable("id") String userId) {
        Utilisateur updated = authFacade.deactivateUser(userId);
        return ResponseEntity.ok(toUserResponse(updated));
    }

    private Map<String, Object> toUserResponse(Utilisateur user) {
        String statut = resolveSubscriptionStatus(user.getId().toString());
        return userViewMapper.toUserView(user, statut);
    }

    private String resolveSubscriptionStatus(String userId) {
        try {
            return subscriptionStatusResolver.normalize(redisStore.getSubscriptionStatus(userId));
        } catch (Exception e) {
            return "NONE";
        }
    }
}
