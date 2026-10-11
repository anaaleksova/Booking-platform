package com.bookingsaas.integration.admin;

import com.bookingsaas.integration.auth.IntegrationPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shapes of the admin "Integrations" page. */
public final class IntegrationAdminDtos {

    private IntegrationAdminDtos() {
    }

    /**
     * @param apiBaseUrl what n8n's credential points at, e.g. http://localhost:8080/api/integrations
     */
    public record Overview(Long businessId, String apiBaseUrl, N8nView n8n, List<ChannelView> channels,
                           List<TokenView> tokens, List<EventView> recentEvents,
                           List<IntegrationPermission> availablePermissions) {
    }

    /** @param signingSecret shown to owners so they can configure signature checks in n8n */
    public record N8nView(String status, String webhookUrl, String signingSecret, Instant updatedAt) {
    }

    /**
     * A social channel. For the MVP the page or account id is entered by hand;
     * OAuth will fill it in later.
     */
    public record ChannelView(String provider, String status, String externalAccountId, String displayName,
                              boolean oauthAvailable) {
    }

    public record TokenView(Long id, String name, String prefix, Set<IntegrationPermission> permissions,
                            Instant createdAt, Instant lastUsedAt, Instant revokedAt) {
    }

    /** @param token the full secret — returned once, at creation, and never again */
    public record CreatedToken(TokenView token, String secret) {
    }

    public record EventView(Long id, String eventType, Long reservationId, UUID deliveryId, String status,
                            int attempts, Integer responseStatus, String lastError, Instant dueAt,
                            Instant deliveredAt, Instant createdAt) {
    }

    public record UpdateN8nRequest(@Size(max = 1000) String webhookUrl, @NotNull Boolean enabled) {
    }

    public record UpdateChannelRequest(@Size(max = 128) String externalAccountId,
                                       @Size(max = 160) String displayName,
                                       @NotNull Boolean enabled) {
    }

    public record CreateTokenRequest(@NotBlank @Size(max = 120) String name,
                                     @NotEmpty Set<IntegrationPermission> permissions) {
    }

    public record TestResult(boolean delivered, Integer httpStatus, String error) {
    }
}
