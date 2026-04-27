package com.fitunity.auth.filter;

import com.fitunity.auth.service.port.TokenProvider;
import com.fitunity.auth.service.port.UserIdentityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenProvider tokenProvider;
    private final UserIdentityService userIdentityService;

    public JwtAuthenticationFilter(TokenProvider tokenProvider, UserIdentityService userIdentityService) {
        this.tokenProvider = tokenProvider;
        this.userIdentityService = userIdentityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                // Check if token is blacklisted
                if (tokenProvider.isTokenBlacklisted(jwt)) {
                    log.debug("Token is blacklisted - rejecting request");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Validate token and extract user details
                if (tokenProvider.validateToken(jwt)) {
                    String userId = tokenProvider.extractUserId(jwt);
                    UserDetails userDetails = userIdentityService.loadUserByUserId(userId);

                    if (userDetails != null) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails,
                                        null,
                                        userDetails.getAuthorities()
                                );
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Successfully authenticated user: {}", userId);
                    }
                } else {
                    log.debug("Token validation failed");
                }
            }
        } catch (Exception e) {
            log.error("Could not set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
