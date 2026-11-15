package com.bookingsaas.integration.meta;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.integration.Integration;
import com.bookingsaas.integration.IntegrationProvider;
import com.bookingsaas.integration.IntegrationRepository;
import com.bookingsaas.integration.IntegrationStatus;
import com.bookingsaas.integration.meta.MetaConnectDtos.CompleteResponse;
import com.bookingsaas.integration.meta.MetaConnectDtos.MetaView;
import com.bookingsaas.integration.meta.MetaConnectDtos.PageChoice;
import com.bookingsaas.integration.meta.MetaConnectDtos.StartResponse;
import com.bookingsaas.integration.meta.MetaGraphClient.MetaGraphException;
import com.bookingsaas.integration.meta.MetaGraphClient.MetaPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * "Connect with Facebook": the owner signs in with Meta, grants the
 * platform's app access to their Page, and the Page's token is kept
 * (encrypted) for this business. It also fills in the Facebook and Instagram
 * channels, so nobody copies ids from Meta's dashboards any more.
 *
 * <p>Meta is called outside any database transaction; only the final save is
 * transactional.
 */
@Service
public class MetaConnectService {

    private static final Logger log = LoggerFactory.getLogger(MetaConnectService.class);
    /** How long the owner may spend in Meta's dialog. */
    private static final Duration STATE_TTL = Duration.ofMinutes(15);
    /** How long the "which Page?" choice stays open. */
    private static final Duration CHOICE_TTL = Duration.ofMinutes(15);

    private final MetaSettings settings;
    private final TokenCipher cipher;
    private final MetaGraphClient graph;
    private final MetaConnectionRepository connections;
    private final MetaConnectSessionRepository sessions;
    private final IntegrationRepository integrations;
    private final CurrentTenant currentTenant;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();

    public MetaConnectService(MetaSettings settings, TokenCipher cipher, MetaGraphClient graph,
                              MetaConnectionRepository connections, MetaConnectSessionRepository sessions,
                              IntegrationRepository integrations, CurrentTenant currentTenant, Clock clock,
                              PlatformTransactionManager transactionManager) {
        this.settings = settings;
        this.cipher = cipher;
        this.graph = graph;
        this.connections = connections;
        this.sessions = sessions;
        this.integrations = integrations;
        this.currentTenant = currentTenant;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public boolean available() {
        return settings.hasApp() && cipher.isConfigured();
    }

    public MetaView view(Long businessId) {
        return connections.findByBusinessId(businessId).map(MetaConnectService::view)
                .orElseGet(() -> MetaView.notConnected(available()));
    }

    /** The address of Meta's login dialog, with a signed state naming this owner and business. */
    public StartResponse start() {
        currentTenant.requireOwner();
        requireAvailable();
        StringBuilder url = new StringBuilder("https://www.facebook.com/").append(settings.graphVersion())
                .append("/dialog/oauth?client_id=").append(encode(settings.appId()))
                .append("&redirect_uri=").append(encode(settings.redirectUri()))
                .append("&response_type=code")
                .append("&state=").append(encode(newState()));
        if (!settings.loginConfigId().isEmpty()) {
            url.append("&config_id=").append(encode(settings.loginConfigId()));
        } else {
            url.append("&scope=").append(encode(String.join(",", settings.scopes())));
        }
        return new StartResponse(url.toString());
    }

    /** Meta sent the owner back with a code: connect their Page, or ask which one. */
    public CompleteResponse complete(String code, String state) {
        currentTenant.requireOwner();
        requireAvailable();
        Long businessId = currentTenant.businessId();
        Long userId = currentTenant.userId();
        checkState(state, businessId, userId);

        String userToken;
        List<MetaPage> pages;
        try {
            userToken = graph.longLivedUserToken(graph.exchangeCode(code, settings.redirectUri()));
            pages = graph.pages(userToken);
        } catch (MetaGraphException ex) {
            throw ApiException.validation(ex.getMessage());
        }
        if (pages.isEmpty()) {
            throw ApiException.validation("Facebook did not share any Page. Connect again and, when Facebook asks,"
                    + " tick the Page of your business — you need to be one of its admins.");
        }
        if (pages.size() == 1) {
            return new CompleteResponse(false, connect(businessId, userId, pages.get(0)), null, null);
        }
        String encrypted = cipher.encrypt(userToken);
        Instant now = clock.instant();
        MetaConnectSession session = transaction.execute(status -> {
            sessions.deleteExpired(now);
            sessions.deleteByBusinessId(businessId);
            return sessions.save(new MetaConnectSession(businessId, userId, encrypted, now, now.plus(CHOICE_TTL)));
        });
        return new CompleteResponse(true, null, Objects.requireNonNull(session).getId(),
                pages.stream().map(page -> choice(page, businessId)).toList());
    }

    /** The owner picked one of their Pages. */
    public MetaView choosePage(UUID sessionId, String pageId) {
        currentTenant.requireOwner();
        requireAvailable();
        Long businessId = currentTenant.businessId();
        Long userId = currentTenant.userId();
        MetaConnectSession session = sessions.findById(sessionId)
                .filter(s -> s.getBusinessId().equals(businessId) && s.getUserId().equals(userId))
                .filter(s -> s.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> ApiException.validation("This sign-in has expired. Click Connect again."));
        List<MetaPage> pages;
        try {
            pages = graph.pages(cipher.decrypt(session.getUserTokenEncrypted()));
        } catch (MetaGraphException ex) {
            throw ApiException.validation(ex.getMessage());
        }
        MetaPage page = pages.stream().filter(p -> p.id().equals(pageId)).findFirst()
                .orElseThrow(() -> ApiException.validation("That Page is not among the ones you shared."));
        MetaView view = connect(businessId, userId, page);
        transaction.executeWithoutResult(status -> sessions.deleteById(sessionId));
        return view;
    }

    /** Forgets the Page and its token; the Facebook and Instagram channels stop taking bookings. */
    public MetaView disconnect() {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        transaction.executeWithoutResult(status -> {
            connections.findByBusinessId(businessId).ifPresent(connections::delete);
            sessions.deleteByBusinessId(businessId);
            for (IntegrationProvider provider : List.of(IntegrationProvider.FACEBOOK, IntegrationProvider.INSTAGRAM)) {
                integrations.findByBusinessIdAndProvider(businessId, provider).ifPresent(channel -> {
                    channel.setExternalAccountId(null);
                    channel.setDisplayName(null);
                    channel.setStatus(IntegrationStatus.NOT_CONNECTED);
                    integrations.save(channel);
                });
            }
        });
        log.info("Business {} disconnected its Facebook Page", businessId);
        return MetaView.notConnected(available());
    }

    // --- helpers -----------------------------------------------------------

    private MetaView connect(Long businessId, Long userId, MetaPage page) {
        if (page.accessToken() == null || page.accessToken().isBlank()) {
            throw ApiException.validation("Facebook did not give access to " + page.name()
                    + ". You need to be an admin of the Page.");
        }
        String encrypted = cipher.encrypt(page.accessToken());
        MetaConnection saved = transaction.execute(status -> {
            connections.findByPageId(page.id())
                    .filter(other -> !other.getBusinessId().equals(businessId))
                    .ifPresent(other -> {
                        throw ApiException.of(ErrorCode.CONFLICT, page.name()
                                + " is already connected to another business on this platform.");
                    });
            MetaConnection connection = connections.findByBusinessId(businessId)
                    .orElseGet(() -> new MetaConnection(businessId));
            connection.setPageId(page.id());
            connection.setPageName(page.name());
            connection.setInstagramAccountId(page.instagramAccountId());
            connection.setInstagramUsername(page.instagramUsername());
            connection.setPageTokenEncrypted(encrypted);
            connection.setConnectedByUserId(userId);
            MetaConnection result = connections.save(connection);

            channel(businessId, IntegrationProvider.FACEBOOK, page.id(), page.name());
            channel(businessId, IntegrationProvider.INSTAGRAM, page.instagramAccountId(),
                    page.instagramUsername() == null ? null : "@" + page.instagramUsername());
            return result;
        });
        log.info("Business {} connected Facebook Page {} (Instagram: {})", businessId, page.id(),
                page.instagramAccountId() == null ? "none" : page.instagramAccountId());
        return view(Objects.requireNonNull(saved));
    }

    /** A channel with an account is connected; without one (no Instagram on the Page), it is not. */
    private void channel(Long businessId, IntegrationProvider provider, String accountId, String displayName) {
        Integration channel = integrations.findByBusinessIdAndProvider(businessId, provider)
                .orElseGet(() -> new Integration(businessId, provider));
        channel.setExternalAccountId(accountId);
        channel.setDisplayName(displayName);
        channel.setStatus(accountId == null ? IntegrationStatus.NOT_CONNECTED : IntegrationStatus.CONNECTED);
        integrations.save(channel);
    }

    private PageChoice choice(MetaPage page, Long businessId) {
        boolean elsewhere = connections.findByPageId(page.id())
                .filter(other -> !other.getBusinessId().equals(businessId)).isPresent();
        return new PageChoice(page.id(), page.name(), page.instagramUsername(), elsewhere);
    }

    /** {@code business-user-expiry-nonce.signature}: Meta hands it back untouched. */
    private String newState() {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        String payload = currentTenant.businessId() + "-" + currentTenant.userId() + "-"
                + clock.instant().plus(STATE_TTL).getEpochSecond() + "-" + HexFormat.of().formatHex(nonce);
        return payload + "." + cipher.sign(payload);
    }

    private void checkState(String state, Long businessId, Long userId) {
        int dot = state.lastIndexOf('.');
        String payload = dot < 0 ? "" : state.substring(0, dot);
        String[] parts = payload.split("-");
        boolean valid = dot > 0 && cipher.verify(payload, state.substring(dot + 1)) && parts.length == 4;
        if (!valid || !parts[0].equals(String.valueOf(businessId)) || !parts[1].equals(String.valueOf(userId))) {
            throw ApiException.validation("This Facebook sign-in was not started here. Click Connect again.");
        }
        if (Long.parseLong(parts[2]) < clock.instant().getEpochSecond()) {
            throw ApiException.validation("The Facebook sign-in took too long. Click Connect again.");
        }
    }

    private void requireAvailable() {
        if (!available()) {
            throw ApiException.validation("Connecting with Facebook is not set up on this platform yet.");
        }
    }

    private static MetaView view(MetaConnection connection) {
        return new MetaView(true, true, connection.getPageId(), connection.getPageName(),
                connection.getInstagramAccountId(), connection.getInstagramUsername(), connection.getUpdatedAt());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
