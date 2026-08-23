package com.bookingsaas.auth.dto;

import java.time.Instant;

/**
 * Token plus the profile the dashboard needs on first paint, so the client
 * does not have to follow every login with a {@code /me} round trip.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        CurrentUserResponse user
) {

    public static AuthResponse of(String token, Instant expiresAt, CurrentUserResponse user) {
        return new AuthResponse(token, "Bearer", expiresAt, user);
    }
}
