package com.bookingsaas.auth;

import com.bookingsaas.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;

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
    /** Fingerprint of the password the token was issued under; see {@link #passwordFingerprint}. */
    static final String CLAIM_PASSWORD_VERSION = "pwv";

    /** Example secrets that appear in this repository (application-dev.yml, .env.example, docker-compose.yml). */
    static final Set<String> PLACEHOLDER_SECRETS = Set.of(
            "local-development-only-secret-please-change-me",
            "change-me-to-a-long-random-value-at-least-32-chars",
            "local-development-secret-change-me-please-32");

    private final SecretKey key;
    private final String issuer;
    private final Duration expiration;

    public JwtService(AppProperties properties, Environment environment) {
        String secret = properties.jwt().secret();
        if (PLACEHOLDER_SECRETS.contains(secret) && !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            // Anyone who has read the repository knows these, and could sign a
            // token for any user. Refuse to start rather than run insecurely.
            throw new IllegalStateException("JWT_SECRET is set to a published example value. "
                    + "Generate a real one, e.g. openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.jwt().issuer();
        this.expiration = Duration.ofMinutes(properties.jwt().expirationMinutes());
    }

    public String issue(Long userId, String email, Long businessId, String passwordHash) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_PASSWORD_VERSION, passwordFingerprint(passwordHash))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)));
        if (businessId != null) {
            builder.claim(CLAIM_BUSINESS_ID, businessId);
        }
        return builder.signWith(key).compact();
    }

    /**
     * A short, one-way fingerprint of the stored password hash. Tokens carry it,
     * and the filter compares it with the current one: changing the password
     * changes the fingerprint, which ends every session issued before. The hash
     * itself never appears in the token.
     */
    public static String passwordFingerprint(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
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
