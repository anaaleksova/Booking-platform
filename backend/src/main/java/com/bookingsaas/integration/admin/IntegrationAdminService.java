package com.bookingsaas.integration.admin;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.integration.Integration;
import com.bookingsaas.integration.IntegrationProvider;
import com.bookingsaas.integration.IntegrationRepository;
import com.bookingsaas.integration.IntegrationStatus;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.ChannelView;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.CreateTokenRequest;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.CreatedToken;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.EventView;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.N8nView;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.Overview;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.TestResult;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.TokenView;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.UpdateChannelRequest;
import com.bookingsaas.integration.admin.IntegrationAdminDtos.UpdateN8nRequest;
import com.bookingsaas.integration.auth.IntegrationPermission;
import com.bookingsaas.integration.auth.IntegrationToken;
import com.bookingsaas.integration.auth.IntegrationTokenService;
import com.bookingsaas.integration.events.IntegrationEvent;
import com.bookingsaas.integration.events.IntegrationEventDispatcher;
import com.bookingsaas.integration.events.WebhookSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The owner's view of integrations: the n8n connection, social channels,
 * API tokens and recent event deliveries. Owner-only throughout — tokens and
 * signing secrets are keys to the business.
 */
@Service
public class IntegrationAdminService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationAdminService.class);
    private static final int RECENT_EVENTS = 20;

    private final IntegrationRepository integrations;
    private final IntegrationTokenService tokens;
    private final IntegrationEventDispatcher dispatcher;
    private final CurrentTenant currentTenant;
    private final String publicApiUrl;
    private final boolean blockPrivateWebhooks;

    public IntegrationAdminService(IntegrationRepository integrations,
                                   IntegrationTokenService tokens,
                                   IntegrationEventDispatcher dispatcher,
                                   CurrentTenant currentTenant,
                                   @Value("${app.integrations.public-api-url:http://localhost:8080}") String publicApiUrl,
                                   @Value("${app.integrations.block-private-webhooks:false}") boolean blockPrivateWebhooks) {
        this.integrations = integrations;
        this.tokens = tokens;
        this.dispatcher = dispatcher;
        this.currentTenant = currentTenant;
        this.publicApiUrl = publicApiUrl;
        this.blockPrivateWebhooks = blockPrivateWebhooks;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Integration n8n = find(businessId, IntegrationProvider.N8N);
        List<ChannelView> channels = List.of(IntegrationProvider.INSTAGRAM, IntegrationProvider.FACEBOOK).stream()
                .map(provider -> channelView(provider, find(businessId, provider)))
                .toList();
        String base = publicApiUrl.endsWith("/") ? publicApiUrl.substring(0, publicApiUrl.length() - 1) : publicApiUrl;
        return new Overview(businessId, base + "/api/integrations", n8nView(n8n), channels,
                tokens.list(businessId).stream().map(IntegrationAdminService::tokenView).toList(),
                dispatcher.recent(businessId, RECENT_EVENTS).stream().map(IntegrationAdminService::eventView).toList(),
                Arrays.asList(IntegrationPermission.values()));
    }

    /** Connects (or disconnects) n8n. A signing secret is generated on first use. */
    @Transactional
    public N8nView updateN8n(UpdateN8nRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        String url = request.webhookUrl() == null || request.webhookUrl().isBlank() ? null : request.webhookUrl().trim();
        if (url != null) {
            validateWebhookUrl(url);
        }
        if (request.enabled() && url == null) {
            throw ApiException.validation("Enter the n8n webhook URL to connect.");
        }
        Integration n8n = findOrNew(businessId, IntegrationProvider.N8N);
        n8n.setWebhookUrl(url);
        if (n8n.getSigningSecret() == null) {
            n8n.setSigningSecret(WebhookSigner.newSecret());
        }
        n8n.setStatus(request.enabled() ? IntegrationStatus.CONNECTED
                : url == null ? IntegrationStatus.NOT_CONNECTED : IntegrationStatus.DISABLED);
        Integration saved = integrations.save(n8n);
        log.info("n8n integration for business {} is now {}", businessId, saved.getStatus());
        return n8nView(saved);
    }

    @Transactional
    public N8nView rotateSigningSecret() {
        currentTenant.requireOwner();
        Integration n8n = findOrNew(currentTenant.businessId(), IntegrationProvider.N8N);
        n8n.setSigningSecret(WebhookSigner.newSecret());
        return n8nView(integrations.save(n8n));
    }

    public TestResult sendTest() {
        currentTenant.requireOwner();
        Integration n8n = find(currentTenant.businessId(), IntegrationProvider.N8N);
        if (n8n == null || n8n.getWebhookUrl() == null) {
            throw ApiException.validation("Save the n8n webhook URL first.");
        }
        IntegrationEventDispatcher.DeliveryResult result = dispatcher.sendTest(n8n);
        return new TestResult(result.delivered(), result.httpStatus(), result.error());
    }

    /**
     * Records which Instagram/Facebook account the business receives messages
     * on. For the MVP the id is entered by hand; the access token for sending
     * lives in n8n's credential store, never here.
     */
    @Transactional
    public ChannelView updateChannel(IntegrationProvider provider, UpdateChannelRequest request) {
        currentTenant.requireOwner();
        if (provider == IntegrationProvider.N8N) {
            throw ApiException.validation("Use the n8n settings for n8n.");
        }
        String accountId = request.externalAccountId() == null || request.externalAccountId().isBlank()
                ? null : request.externalAccountId().trim();
        if (request.enabled() && accountId == null) {
            throw ApiException.validation("Enter the " + label(provider) + " account or page id to connect.");
        }
        Integration channel = findOrNew(currentTenant.businessId(), provider);
        channel.setExternalAccountId(accountId);
        channel.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? null : request.displayName().trim());
        channel.setStatus(request.enabled() ? IntegrationStatus.CONNECTED
                : accountId == null ? IntegrationStatus.NOT_CONNECTED : IntegrationStatus.DISABLED);
        return channelView(provider, integrations.save(channel));
    }

    @Transactional
    public CreatedToken createToken(CreateTokenRequest request) {
        currentTenant.requireOwner();
        IntegrationTokenService.IssuedToken issued = tokens.issue(currentTenant.businessId(), request.name(),
                Set.copyOf(request.permissions()), currentTenant.userId());
        log.info("Integration token {} created for business {}", issued.token().getId(), currentTenant.businessId());
        return new CreatedToken(tokenView(issued.token()), issued.secret());
    }

    @Transactional
    public TokenView revokeToken(Long tokenId) {
        currentTenant.requireOwner();
        IntegrationToken token = tokens.revoke(currentTenant.businessId(), tokenId);
        log.info("Integration token {} revoked for business {}", tokenId, currentTenant.businessId());
        return tokenView(token);
    }

    // --- helpers -----------------------------------------------------------

    /**
     * Only http(s), and — where configured — no private addresses: the
     * platform itself sends these requests, so a tenant must not be able to
     * point it at internal services.
     */
    private void validateWebhookUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("The webhook URL is not valid.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
            throw ApiException.validation("The webhook URL must start with http:// or https://.");
        }
        if (blockPrivateWebhooks) {
            try {
                for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                    if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                            || address.isAnyLocalAddress()) {
                        throw ApiException.validation("The webhook URL must point to a public address.");
                    }
                }
            } catch (UnknownHostException ex) {
                throw ApiException.validation("The webhook host cannot be resolved.");
            }
        }
    }

    private Integration find(Long businessId, IntegrationProvider provider) {
        return integrations.findByBusinessIdAndProvider(businessId, provider).orElse(null);
    }

    private Integration findOrNew(Long businessId, IntegrationProvider provider) {
        return integrations.findByBusinessIdAndProvider(businessId, provider)
                .orElseGet(() -> new Integration(businessId, provider));
    }

    private static N8nView n8nView(Integration n8n) {
        if (n8n == null) {
            return new N8nView(IntegrationStatus.NOT_CONNECTED.name(), null, null, null);
        }
        return new N8nView(n8n.getStatus().name(), n8n.getWebhookUrl(), n8n.getSigningSecret(), n8n.getUpdatedAt());
    }

    private static ChannelView channelView(IntegrationProvider provider, Integration channel) {
        return channel == null
                ? new ChannelView(provider.name(), IntegrationStatus.NOT_CONNECTED.name(), null, null, false)
                : new ChannelView(provider.name(), channel.getStatus().name(), channel.getExternalAccountId(),
                        channel.getDisplayName(), false);
    }

    private static TokenView tokenView(IntegrationToken token) {
        return new TokenView(token.getId(), token.getName(), token.getTokenPrefix(), token.permissionSet(),
                token.getCreatedAt(), token.getLastUsedAt(), token.getRevokedAt());
    }

    private static EventView eventView(IntegrationEvent event) {
        return new EventView(event.getId(), event.getEventType(), event.getReservationId(), event.getDeliveryId(),
                event.getStatus().name(), event.getAttempts(), event.getResponseStatus(), event.getLastError(),
                event.getDueAt(), event.getDeliveredAt(), event.getCreatedAt());
    }

    private static String label(IntegrationProvider provider) {
        return provider == IntegrationProvider.INSTAGRAM ? "Instagram" : "Facebook";
    }
}
