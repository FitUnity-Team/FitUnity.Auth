package com.fitunity.auth.service.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshCookieServiceTest {

    @Test
    void shouldSetRefreshCookieWithExpectedAttributes() {
        RefreshCookieService service = new RefreshCookieService(true, "Strict");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setRefreshTokenCookie(response, "raw-refresh");

        Cookie cookie = response.getCookie("refreshToken");
        assertEquals("raw-refresh", cookie.getValue());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals(30 * 24 * 60 * 60, cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());

        List<String> headers = response.getHeaders("Set-Cookie");
        assertEquals(2, headers.size());
        assertTrue(headers.stream().anyMatch(h -> h.contains("SameSite=Strict")));
    }

    @Test
    void shouldClearRefreshCookie() {
        RefreshCookieService service = new RefreshCookieService(true, "Strict");
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearRefreshTokenCookie(response);

        Cookie cookie = response.getCookie("refreshToken");
        assertEquals("", cookie.getValue());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals(0, cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());

        List<String> headers = response.getHeaders("Set-Cookie");
        assertEquals(2, headers.size());
        assertTrue(headers.stream().anyMatch(h -> h.contains("SameSite=Strict")));
    }

    @Test
    void shouldExtractRefreshTokenFromRequestCookie() {
        RefreshCookieService service = new RefreshCookieService(false, "Lax");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "v"), new Cookie("refreshToken", "abc"));

        assertEquals("abc", service.getRefreshTokenFromCookie(request));
    }

    @Test
    void shouldReturnNullWhenRefreshTokenCookieMissing() {
        RefreshCookieService service = new RefreshCookieService(false, "Lax");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "v"));

        assertNull(service.getRefreshTokenFromCookie(request));
    }
}
