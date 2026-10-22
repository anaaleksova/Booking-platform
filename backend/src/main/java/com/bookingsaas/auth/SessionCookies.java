package com.bookingsaas.auth;

import com.bookingsaas.common.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

/**
 * The browser session lives in an httpOnly cookie, not in script-readable
 * storage: an injected script can still act on the page while it runs, but
 * cannot carry the session away and keep using it.
 *
 * <p>{@code SameSite=Lax} keeps it off cross-site POSTs, and the JWT filter
 * additionally requires {@code X-Requested-With} on state-changing requests
 * authenticated by the cookie, a header no cross-site form can send.
 */
@Component
public class SessionCookies {

    /** Sent by the SPA on every request; required for writes made with the cookie. */
    public static final String CSRF_HEADER = "X-Requested-With";

    private static final String PATH = "/api";

    private final String name;
    private final boolean secure;

    public SessionCookies(AppProperties properties) {
        AppProperties.Session session = properties.session();
        this.name = session == null ? "booking_session" : session.cookieName();
        this.secure = session != null && session.secureCookie();
    }

    public String issue(String token, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        return base(token).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build().toString();
    }

    public String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()) && !cookie.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secure).sameSite("Lax").path(PATH);
    }
}
