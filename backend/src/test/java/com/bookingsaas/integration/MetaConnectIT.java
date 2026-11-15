package com.bookingsaas.integration;

import com.bookingsaas.integration.meta.MetaGraphClient;
import com.bookingsaas.integration.meta.MetaGraphClient.MetaPage;
import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** "Connect with Facebook", with the Graph API stood in for. */
class MetaConnectIT extends AbstractIntegrationTest {

    private static final MetaPage BISTRO = new MetaPage("111", "Harbor Bistro", "page-token-bistro", "17841", "harborbistro");
    private static final MetaPage BAR = new MetaPage("222", "Harbor Bar", "page-token-bar", null, null);

    @MockBean
    MetaGraphClient graph;

    String owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndLogin("owner@bistro.test", "Harbor Bistro", "RESTAURANT");
        when(graph.exchangeCode(anyString(), anyString())).thenReturn("short-user-token");
        when(graph.longLivedUserToken("short-user-token")).thenReturn("long-user-token");
    }

    @Test
    void connect_sends_the_owner_to_metas_dialog_with_a_signed_state() throws Exception {
        String url = send(post("/api/admin/integrations/meta/start"), owner, null, 200).get("authorizeUrl").asText();
        assertThat(url).startsWith("https://www.facebook.com/v21.0/dialog/oauth?client_id=1000000000000001")
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fadmin%2Fintegrations")
                .contains("scope=pages_show_list%2C").contains("instagram_manage_messages")
                .contains("state=");
        assertThat(send(get("/api/admin/integrations"), owner, null, 200).at("/meta/available").asBoolean()).isTrue();
    }

    @Test
    void with_one_page_the_business_is_connected_and_both_channels_filled_in() throws Exception {
        when(graph.pages("long-user-token")).thenReturn(List.of(BISTRO));

        JsonNode done = complete(owner, state(owner));
        assertThat(done.get("needsPageChoice").asBoolean()).isFalse();
        assertThat(done.at("/connection/pageName").asText()).isEqualTo("Harbor Bistro");

        JsonNode overview = send(get("/api/admin/integrations"), owner, null, 200);
        assertThat(overview.at("/meta/connected").asBoolean()).isTrue();
        assertThat(overview.at("/meta/instagramUsername").asText()).isEqualTo("harborbistro");
        assertThat(channel(overview, "FACEBOOK").get("externalAccountId").asText()).isEqualTo("111");
        assertThat(channel(overview, "INSTAGRAM").get("externalAccountId").asText()).isEqualTo("17841");
        assertThat(channel(overview, "INSTAGRAM").get("displayName").asText()).isEqualTo("@harborbistro");
        assertThat(channel(overview, "INSTAGRAM").get("status").asText()).isEqualTo("CONNECTED");
        assertThat(overview.toString()).doesNotContain("page-token-bistro");

        String stored = jdbcTemplate.queryForObject("SELECT page_token_encrypted FROM meta_connection", String.class);
        assertThat(stored).startsWith("v1:").doesNotContain("page-token-bistro");
    }

    @Test
    void an_owner_with_several_pages_chooses_one() throws Exception {
        when(graph.pages("long-user-token")).thenReturn(List.of(BISTRO, BAR));

        JsonNode choice = complete(owner, state(owner));
        assertThat(choice.get("needsPageChoice").asBoolean()).isTrue();
        assertThat(choice.get("pages")).hasSize(2);
        assertThat(choice.toString()).doesNotContain("page-token");

        JsonNode connected = send(post("/api/admin/integrations/meta/choose-page"), owner, """
                {"sessionId":"%s","pageId":"222"}""".formatted(choice.get("sessionId").asText()), 200);
        assertThat(connected.get("pageName").asText()).isEqualTo("Harbor Bar");

        // No Instagram on that Page: only Facebook takes bookings.
        JsonNode overview = send(get("/api/admin/integrations"), owner, null, 200);
        assertThat(channel(overview, "FACEBOOK").get("status").asText()).isEqualTo("CONNECTED");
        assertThat(channel(overview, "INSTAGRAM").get("status").asText()).isEqualTo("NOT_CONNECTED");
        // The choice is used up.
        send(post("/api/admin/integrations/meta/choose-page"), owner, """
                {"sessionId":"%s","pageId":"111"}""".formatted(choice.get("sessionId").asText()), 400);
    }

    @Test
    void a_page_belongs_to_one_business_only() throws Exception {
        when(graph.pages("long-user-token")).thenReturn(List.of(BISTRO));
        complete(owner, state(owner));

        String rival = registerAndLogin("owner@rival.test", "Rival Bistro", "RESTAURANT");
        send(post("/api/admin/integrations/meta/complete"), rival, body(state(rival)), 409);

        when(graph.pages("long-user-token")).thenReturn(List.of(BISTRO, BAR));
        JsonNode choice = complete(rival, state(rival));
        assertThat(choice.at("/pages/0/connectedElsewhere").asBoolean()).isTrue();
        assertThat(choice.at("/pages/1/connectedElsewhere").asBoolean()).isFalse();
    }

    @Test
    void a_sign_in_started_by_another_business_or_tampered_with_is_refused() throws Exception {
        when(graph.pages("long-user-token")).thenReturn(List.of(BISTRO));
        String other = registerAndLogin("owner@other.test", "Other Place", "RESTAURANT");

        send(post("/api/admin/integrations/meta/complete"), owner, body(state(other)), 400);
        String forged = state(owner).replaceFirst("^\\d+", "999");
        send(post("/api/admin/integrations/meta/complete"), owner, body(forged), 400);
        assertThat(send(get("/api/admin/integrations"), owner, null, 200).at("/meta/connected").asBoolean()).isFalse();
    }

    @Test
    void disconnecting_forgets_the_page_and_stops_both_channels() throws Exception {
        when(graph.pages("long-user-token")).thenReturn(List.of(BISTRO));
        complete(owner, state(owner));

        // While signed in, the ids are not edited by hand.
        send(put("/api/admin/integrations/channels/facebook"), owner, """
                {"externalAccountId":"999","enabled":true}""", 400);

        send(delete("/api/admin/integrations/meta"), owner, null, 200);
        JsonNode overview = send(get("/api/admin/integrations"), owner, null, 200);
        assertThat(overview.at("/meta/connected").asBoolean()).isFalse();
        assertThat(channel(overview, "FACEBOOK").get("status").asText()).isEqualTo("NOT_CONNECTED");
        assertThat(channel(overview, "INSTAGRAM").path("externalAccountId").isTextual()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM meta_connection", Integer.class)).isZero();
    }

    @Test
    void meta_refusing_the_code_is_shown_to_the_owner() throws Exception {
        when(graph.exchangeCode(eq("expired"), anyString()))
                .thenThrow(new MetaGraphClient.MetaGraphException("Facebook refused the request: code expired"));
        JsonNode error = send(post("/api/admin/integrations/meta/complete"), owner, """
                {"code":"expired","state":"%s"}""".formatted(state(owner)), 400);
        assertThat(error.toString()).contains("code expired");
    }

    @Test
    void the_https_return_address_forwards_only_metas_answer_to_the_integrations_page() throws Exception {
        mockMvc.perform(get("/api/public/meta/return?code=abc&state=1-2-3-x.sig&next=https://evil.example"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location",
                        "http://localhost:5173/admin/integrations?code=abc&state=1-2-3-x.sig"));
    }

    @Test
    void only_owners_connect() throws Exception {
        String customer = customerToken("guest@example.com");
        send(post("/api/admin/integrations/meta/start"), customer, null, 403);
    }

    // --- helpers -----------------------------------------------------------

    private String state(String ownerToken) throws Exception {
        String url = send(post("/api/admin/integrations/meta/start"), ownerToken, null, 200).get("authorizeUrl").asText();
        String encoded = url.replaceAll(".*[?&]state=([^&]+).*", "$1");
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private JsonNode complete(String ownerToken, String state) throws Exception {
        return send(post("/api/admin/integrations/meta/complete"), ownerToken, body(state), 200);
    }

    private static String body(String state) {
        return """
                {"code":"the-code","state":"%s"}""".formatted(state);
    }

    private static JsonNode channel(JsonNode overview, String provider) {
        for (JsonNode channel : overview.get("channels")) {
            if (channel.get("provider").asText().equals(provider)) {
                return channel;
            }
        }
        throw new AssertionError("No " + provider + " channel");
    }
}
