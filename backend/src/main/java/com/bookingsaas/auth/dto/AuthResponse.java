package com.bookingsaas.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * A new session plus the profile the dashboard needs on first paint, so the
 * client does not have to follow every login with a {@code /me} round trip.
 *
 * <p>The token itself is never written to the body: {@code SessionCookieAdvice}
 * moves it into an httpOnly cookie, out of reach of any script on the page.
 */
public record AuthResponse(
        @JsonIgnore String accessToken,
        Instant expiresAt,
        CurrentUserResponse user
) {

    public static AuthResponse of(String token, Instant expiresAt, CurrentUserResponse user) {
        return new AuthResponse(token, expiresAt, user);
    }
}
