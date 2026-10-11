package com.bookingsaas.integration.auth;

import com.bookingsaas.common.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Issues, checks and revokes integration tokens. */
@Service
public class IntegrationTokenService {

    /** Recognisable in logs and secret scanners, and never mistaken for a JWT. */
    public static final String TOKEN_PREFIX = "bkp_";
    /** last_used_at is a hint for the admin, not an audit log: one write per minute is plenty. */
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IntegrationTokenRepository repository;
    private final Clock clock;

    public IntegrationTokenService(IntegrationTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** A new token and, once only, its secret value. */
    public record IssuedToken(IntegrationToken token, String secret) {
    }

    @Transactional
    public IssuedToken issue(Long businessId, String name, Set<IntegrationPermission> permissions, Long userId) {
        if (permissions.isEmpty()) {
            throw ApiException.validation("Choose at least one permission.");
        }
        byte[] bytes = new byte[30];
        RANDOM.nextBytes(bytes);
        String secret = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        IntegrationToken token = new IntegrationToken();
        token.setBusinessId(businessId);
        token.setName(name.trim());
        token.setTokenPrefix(secret.substring(0, 12));
        token.setTokenHash(hash(secret));
        token.setPermissionSet(permissions);
        token.setCreatedByUserId(userId);
        return new IssuedToken(repository.save(token), secret);
    }

    /** The principal behind a presented token, if it is known and not revoked. */
    @Transactional
    public Optional<IntegrationPrincipal> authenticate(String presented) {
        if (presented == null || !presented.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(presented))
                .filter(token -> !token.isRevoked())
                .map(token -> {
                    Instant now = clock.instant();
                    if (token.getLastUsedAt() == null
                            || token.getLastUsedAt().plus(LAST_USED_RESOLUTION).isBefore(now)) {
                        token.setLastUsedAt(now);
                        repository.save(token);
                    }
                    return new IntegrationPrincipal(token.getId(), token.getBusinessId(), token.getName(),
                            Set.copyOf(token.permissionSet()));
                });
    }

    @Transactional(readOnly = true)
    public List<IntegrationToken> list(Long businessId) {
        return repository.findByBusinessIdOrderByCreatedAtDesc(businessId);
    }

    @Transactional
    public IntegrationToken revoke(Long businessId, Long tokenId) {
        IntegrationToken token = repository.findByIdAndBusinessId(tokenId, businessId)
                .orElseThrow(() -> ApiException.notFound("Integration token", tokenId));
        if (!token.isRevoked()) {
            token.setRevokedAt(clock.instant());
            repository.save(token);
        }
        return token;
    }

    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
