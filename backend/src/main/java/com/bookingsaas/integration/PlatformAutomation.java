package com.bookingsaas.integration;

import com.bookingsaas.integration.meta.MetaConnectionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * The platform's own n8n, which answers the chats of every business that
 * connected its Facebook Page by signing in. It calls the integration API with
 * one platform token (acting for any business) and receives the booking
 * events of those businesses on one webhook.
 *
 * <p>A business that runs its own n8n (Admin → Integrations → n8n) keeps it
 * until it connects with Facebook; from then on the platform's n8n answers it.
 */
@Component
public class PlatformAutomation {

    /** Shorter tokens are refused: this one opens every business. */
    static final int MIN_TOKEN_LENGTH = 32;

    private final String token;
    private final String webhookUrl;
    private final String signingSecret;
    private final MetaConnectionRepository metaConnections;
    private final IntegrationRepository integrations;

    public PlatformAutomation(@Value("${app.integrations.platform.token:}") String token,
                              @Value("${app.integrations.platform.webhook-url:}") String webhookUrl,
                              @Value("${app.integrations.platform.signing-secret:}") String signingSecret,
                              MetaConnectionRepository metaConnections,
                              IntegrationRepository integrations) {
        this.token = token.trim();
        this.webhookUrl = webhookUrl.trim();
        this.signingSecret = signingSecret.trim();
        this.metaConnections = metaConnections;
        this.integrations = integrations;
        if (!this.token.isEmpty() && this.token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalStateException("INTEGRATIONS_PLATFORM_TOKEN must be at least " + MIN_TOKEN_LENGTH
                    + " characters");
        }
    }

    /** Where a business's booking events go and how they are signed. */
    public record WebhookTarget(String url, String signingSecret) {
    }

    public boolean isPlatformToken(String presented) {
        return !token.isEmpty() && presented != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The platform's webhook for a business connected with Facebook, else the
     * business's own n8n when it is switched on, else nothing (no events).
     */
    public Optional<WebhookTarget> webhookFor(Long businessId) {
        if (!webhookUrl.isEmpty() && metaConnections.findByBusinessId(businessId).isPresent()) {
            return Optional.of(new WebhookTarget(webhookUrl, signingSecret.isEmpty() ? null : signingSecret));
        }
        return integrations.findByBusinessIdAndProvider(businessId, IntegrationProvider.N8N)
                .filter(n8n -> n8n.isConnected() && n8n.getWebhookUrl() != null)
                .map(n8n -> new WebhookTarget(n8n.getWebhookUrl(), n8n.getSigningSecret()));
    }
}
