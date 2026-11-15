package com.bookingsaas.integration;

import com.bookingsaas.integration.meta.MetaGraphClient;
import com.bookingsaas.integration.meta.MetaGraphClient.MetaPage;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The platform's own n8n: one token that acts for every business, the list of
 * connected Pages it polls, and booking events of connected businesses routed
 * to its webhook.
 */
class PlatformAutomationIT extends AbstractSpaIntegrationTest {

    static final String PLATFORM = "test-platform-token-0123456789abcdefghij";

    @MockBean
    MetaGraphClient graph;

    @Test
    void the_platform_token_acts_for_any_business_and_a_business_token_does_not() throws Exception {
        integration(get("/api/integrations/businesses/" + businessId + "/services"), PLATFORM, null, 200);
        assertThat(integration(get("/api/integrations/me"), PLATFORM, null, 200).has("businessId")).isFalse();

        String other = registerAndLogin("owner@other.test", "Other Spa");
        long otherId = send(get("/api/admin/business"), other, null, 200).get("id").asLong();
        integration(get("/api/integrations/businesses/" + otherId + "/services"), PLATFORM, null, 200);
        integration(get("/api/integrations/businesses/" + otherId + "/services"), token, null, 403);

        integration(get("/api/integrations/platform/channels"), token, null, 403);
        integration(get("/api/integrations/platform/channels"), "test-platform-token-wrong-0123456789ab", null, 401);
    }

    @Test
    void connected_pages_are_listed_with_their_tokens_for_the_platform() throws Exception {
        assertThat(integration(get("/api/integrations/platform/channels"), PLATFORM, null, 200)).isEmpty();
        connect(owner, new MetaPage("555", "Serenity Page", "page-token-555", "1784", "serenity"));

        JsonNode channels = integration(get("/api/integrations/platform/channels"), PLATFORM, null, 200);
        assertThat(channels).hasSize(1);
        assertThat(channels.at("/0/businessId").asLong()).isEqualTo(businessId);
        assertThat(channels.at("/0/pageId").asText()).isEqualTo("555");
        assertThat(channels.at("/0/instagramAccountId").asText()).isEqualTo("1784");
        assertThat(channels.at("/0/pageToken").asText()).isEqualTo("page-token-555");

        assertThat(integration(get("/api/integrations/platform/channels?businessId=" + businessId), PLATFORM, null,
                200)).hasSize(1);
        assertThat(integration(get("/api/integrations/platform/channels?businessId=999"), PLATFORM, null, 200))
                .isEmpty();
    }

    @Test
    void a_business_connected_with_facebook_gets_booking_events_without_its_own_n8n() throws Exception {
        // No n8n of its own: nothing is queued.
        book("1001", "10:00");
        assertThat(events()).isZero();

        connect(owner, new MetaPage("555", "Serenity Page", "page-token-555", null, null));
        book("1002", "12:00");
        assertThat(events()).isPositive();
    }

    // --- helpers -----------------------------------------------------------

    private void connect(String ownerToken, MetaPage page) throws Exception {
        when(graph.exchangeCode(anyString(), anyString())).thenReturn("short");
        when(graph.longLivedUserToken("short")).thenReturn("long");
        when(graph.pages("long")).thenReturn(List.of(page));
        String url = send(post("/api/admin/integrations/meta/start"), ownerToken, null, 200).get("authorizeUrl").asText();
        String state = URLDecoder.decode(url.replaceAll(".*[?&]state=([^&]+).*", "$1"), StandardCharsets.UTF_8);
        send(post("/api/admin/integrations/meta/complete"), ownerToken, """
                {"code":"c","state":"%s"}""".formatted(state), 200);
    }

    private void book(String igUser, String time) throws Exception {
        integration(post("/api/integrations/businesses/" + businessId + "/reservations")
                .header("Idempotency-Key", "key-" + igUser + "-0000"), PLATFORM,
                reservationBody(classicId, 1, time, igUser), 201);
    }

    private int events() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM integration_event WHERE business_id = ?",
                Integer.class, businessId);
        return count == null ? 0 : count;
    }
}
