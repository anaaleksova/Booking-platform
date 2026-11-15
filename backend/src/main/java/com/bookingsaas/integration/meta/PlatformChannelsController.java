package com.bookingsaas.integration.meta;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessRepository;
import com.bookingsaas.integration.auth.IntegrationAccess;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * For the platform's own n8n only: every Facebook Page businesses connected,
 * with its access token, so the inbox pollers can read each Page's chats and
 * replies go out as that Page.
 */
@RestController
@RequestMapping("/api/integrations/platform/channels")
@Tag(name = "Integration API · Platform")
public class PlatformChannelsController {

    private static final Logger log = LoggerFactory.getLogger(PlatformChannelsController.class);

    private final IntegrationAccess access;
    private final MetaConnectionRepository connections;
    private final BusinessRepository businesses;
    private final TokenCipher cipher;

    public PlatformChannelsController(IntegrationAccess access, MetaConnectionRepository connections,
                                      BusinessRepository businesses, TokenCipher cipher) {
        this.access = access;
        this.connections = connections;
        this.businesses = businesses;
        this.cipher = cipher;
    }

    /**
     * @param instagramAccountId null when no Instagram account is linked to the Page
     */
    public record Channel(Long businessId, String businessName, String pageId, String pageName,
                          String instagramAccountId, String instagramUsername, String pageToken) {
    }

    /** Connected Pages of active businesses; {@code businessId} narrows it to one. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<Channel> channels(@RequestParam(required = false) Long businessId) {
        access.requirePlatform();
        List<MetaConnection> all = businessId == null ? connections.findAll()
                : connections.findByBusinessId(businessId).map(List::of).orElse(List.of());
        Map<Long, Business> owners = businesses.findAllById(all.stream().map(MetaConnection::getBusinessId).toList())
                .stream().collect(Collectors.toMap(Business::getId, Function.identity()));
        List<Channel> channels = new ArrayList<>();
        for (MetaConnection connection : all) {
            Business business = owners.get(connection.getBusinessId());
            if (business == null || !business.isActive()) {
                continue;   // a business taken offline takes no chat bookings
            }
            String token;
            try {
                token = cipher.decrypt(connection.getPageTokenEncrypted());
            } catch (IllegalStateException ex) {
                // META_TOKEN_KEY changed: the owner has to connect again.
                log.warn("Cannot read the Page token of business {}: {}", connection.getBusinessId(), ex.getMessage());
                continue;
            }
            channels.add(new Channel(business.getId(), business.getName(), connection.getPageId(),
                    connection.getPageName(), connection.getInstagramAccountId(), connection.getInstagramUsername(),
                    token));
        }
        return channels;
    }
}
