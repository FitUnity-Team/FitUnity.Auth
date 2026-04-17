package com.fitunity.auth.service.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieService {

    private final boolean cookieSecure;
    private final String cookieSameSite;

    public RefreshCookieService(
            @Value("${cookie.secure:true}") boolean cookieSecure,
            @Value("${cookie.same-site:Strict}") String cookieSameSite
    ) {
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) Duration.ofDays(30).getSeconds());
        response.addCookie(cookie);
        response.addHeader("Set-Cookie", buildSameSiteCookieHeader(refreshToken, (int) Duration.ofDays(30).getSeconds()));
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        response.addHeader("Set-Cookie", buildSameSiteCookieHeader("", 0));
    }

    public String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String buildSameSiteCookieHeader(String value, int maxAgeSeconds) {
        StringBuilder header = new StringBuilder("refreshToken=")
                .append(value)
                .append("; Path=/api/auth; Max-Age=")
                .append(maxAgeSeconds)
                .append("; HttpOnly");

        if (cookieSecure) {
            header.append("; Secure");
        }
        if (cookieSameSite != null && !cookieSameSite.isBlank()) {
            header.append("; SameSite=").append(cookieSameSite);
        }
        return header.toString();
    }
}
