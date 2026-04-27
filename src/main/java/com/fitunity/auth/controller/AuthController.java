package com.fitunity.auth.controller;

import com.fitunity.auth.dto.AuthResponse;
import com.fitunity.auth.dto.LoginRequest;
import com.fitunity.auth.dto.RefreshResponse;
import com.fitunity.auth.dto.RegisterRequest;
import com.fitunity.auth.service.port.AuthFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthFacade authFacade;

    public AuthController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    /**
     * POST /register - Register a new user
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for email: {}", request.getEmail());
        authFacade.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Inscription réussite"));
    }

    /**
     * POST /login - Authenticate user
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        log.info("Login request for email: {}", request.getEmail());
        AuthResponse authResponse = authFacade.login(request, response);
        return ResponseEntity.ok(authResponse);
    }

    /**
     * POST /refresh - Refresh access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("Token refresh request");
        RefreshResponse refreshResponse = authFacade.refresh(request, response);
        return ResponseEntity.ok(refreshResponse);
    }

    /**
     * POST /logout - Logout user
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader("Authorization") String authorization,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        log.info("Logout request");

        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7);
        }

        authFacade.logout(accessToken, request, response);
        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie"));
    }
}
