package com.bookingsaas.auth;

import com.bookingsaas.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies the stateless access token.
 *
 * <p>The token carries the user id and the business the user is acting as, but
 * neither is trusted on its own: the filter re-reads the memberships from the
 * database on every request, so revoking access takes effect immediately.
 */
@Service
public class JwtService {

    static final String CLAIM_BUSINESS_ID = "bid";
    static final String CLAIM_EMAIL = "email";

    private final SecretKey key;
    private final String issuer;
    private final Duration expiration;

    public JwtService(AppProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.jwt().issuer();
        this.expiration = Duration.ofMinutes(properties.jwt().expirationMinutes());
    }

    public String issue(Long userId, String email, Long businessId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)));
        if (businessId != null) {
            builder.claim(CLAIM_BUSINESS_ID, businessId);
        }
        return builder.signWith(key).compact();
    }

    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
    }

    public Duration expiration() {
        return expiration;
    }
}
