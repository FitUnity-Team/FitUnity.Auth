package com.fitunity.auth.service.port;

import com.fitunity.auth.domain.Role;
import com.fitunity.auth.domain.Utilisateur;
import com.fitunity.auth.dto.AuthResponse;
import com.fitunity.auth.dto.LoginRequest;
import com.fitunity.auth.dto.RefreshResponse;
import com.fitunity.auth.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthFacade {

    void register(RegisterRequest request);

    AuthResponse login(LoginRequest request, HttpServletResponse response);

    RefreshResponse refresh(HttpServletRequest request, HttpServletResponse response);

    void logout(String accessToken, HttpServletRequest request, HttpServletResponse response);

    Utilisateur updateRole(String userId, Role role);

    Utilisateur activateUser(String userId);

    Utilisateur deactivateUser(String userId);
}
