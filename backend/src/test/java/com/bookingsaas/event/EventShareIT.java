package com.bookingsaas.event;

import com.bookingsaas.integration.meta.MetaGraphClient;
import com.bookingsaas.integration.meta.MetaGraphClient.MetaGraphException;
import com.bookingsaas.integration.meta.MetaGraphClient.MetaPage;
import com.bookingsaas.integration.meta.MetaGraphClient.PublishedPost;
import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sharing an event on the business's Facebook Page and Instagram, with the Graph API stood in for. */
class EventShareIT extends AbstractIntegrationTest {

    @MockBean
    MetaGraphClient graph;

    String owner;
    long eventId;
    String imageId;

    @BeforeEach
    void setUp() throws Exception {
        owner = registerAndLogin("owner@bistro.test", "Harbor Bistro", "RESTAURANT");
        imageId = uploadImage(owner);
        eventId = send(post("/api/admin/events"), owner, """
                {"title":"Jazz night","description":"Live jazz by the lake.","performer":"The Quartet",
                 "imageMediaId":"%s","date":"%s","startTime":"21:00","endTime":"23:30"}
                """.formatted(imageId, LocalDate.now().plusDays(5)), 201).get("id").asLong();
    }

    @Test
    void without_a_connected_page_nothing_can_be_shared() throws Exception {
        JsonNode preview = send(get("/api/admin/events/" + eventId + "/share"), owner, null, 200);
        assertThat(preview.get("facebookUnavailable").asText()).contains("Connect your Facebook Page");
        // ASCII parts only: the test client reads the response as ISO-8859-1, so "—" and the emoji come back mangled.
        assertThat(preview.get("suggestedCaption").asText()).startsWith("Jazz night").contains("The Quartet")
                .contains("21:00").contains("23:30").contains("Live jazz by the lake.");
        send(post("/api/admin/events/" + eventId + "/share"), owner, body(true, false), 400);
    }

    @Test
    void an_event_is_posted_on_both_with_the_picture_as_a_public_jpeg() throws Exception {
        connect(new MetaPage("111", "Harbor Bistro", "page-token", "1784", "harborbistro"));
        String picture = "https://public.example.test/api/public/media/" + imageId + ".jpg";
        when(graph.postPagePhoto("111", "page-token", picture, "Come along!"))
                .thenReturn(new PublishedPost("111_9", "https://www.facebook.com/111_9"));
        when(graph.postInstagramImage("1784", "page-token", picture, "Come along!"))
                .thenReturn(new PublishedPost("ig-5", "https://www.instagram.com/p/abc/"));

        JsonNode preview = send(get("/api/admin/events/" + eventId + "/share"), owner, null, 200);
        assertThat(preview.has("facebookUnavailable")).isFalse();
        assertThat(preview.has("instagramUnavailable")).isFalse();
        assertThat(preview.get("instagramUsername").asText()).isEqualTo("harborbistro");

        JsonNode result = send(post("/api/admin/events/" + eventId + "/share"), owner, body(true, true), 200);
        assertThat(result.at("/posts/0/status").asText()).isEqualTo("POSTED");
        assertThat(result.at("/posts/1/permalink").asText()).isEqualTo("https://www.instagram.com/p/abc/");

        JsonNode listed = send(get("/api/admin/events"), owner, null, 200).get(0);
        assertThat(listed.get("sharedOn")).hasSize(2);
        assertThat(send(get("/api/admin/events/" + eventId + "/share"), owner, null, 200).get("posts")).hasSize(2);
    }

    @Test
    void the_public_jpeg_is_a_real_jpeg_even_for_a_png_upload() throws Exception {
        byte[] jpeg = mockMvc.perform(get("/api/public/media/" + imageId + ".jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(jpeg[0] & 0xFF).isEqualTo(0xFF);
        assertThat(jpeg[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void instagram_needs_a_picture_and_facebook_then_posts_text() throws Exception {
        connect(new MetaPage("111", "Harbor Bistro", "page-token", "1784", "harborbistro"));
        send(put("/api/admin/events/" + eventId), owner, """
                {"title":"Jazz night","date":"%s","startTime":"21:00"}""".formatted(LocalDate.now().plusDays(5)), 200);
        when(graph.postPageText("111", "page-token", "No picture")).thenReturn(new PublishedPost("111_7", null));

        assertThat(send(get("/api/admin/events/" + eventId + "/share"), owner, null, 200)
                .get("instagramUnavailable").asText()).contains("need a picture");
        send(post("/api/admin/events/" + eventId + "/share"), owner, """
                {"facebook":false,"instagram":true,"caption":"No picture"}""", 400);
        send(post("/api/admin/events/" + eventId + "/share"), owner, """
                {"facebook":true,"instagram":false,"caption":"No picture"}""", 200);
        verify(graph).postPageText("111", "page-token", "No picture");
        verify(graph, never()).postPagePhoto(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void a_refusal_on_one_platform_does_not_stop_the_other_and_says_what_to_do() throws Exception {
        connect(new MetaPage("111", "Harbor Bistro", "page-token", "1784", "harborbistro"));
        when(graph.postPagePhoto(eq("111"), anyString(), anyString(), anyString())).thenThrow(new MetaGraphException(
                "Facebook refused the request: (#200) The permission(s) pages_manage_posts are not available"));
        when(graph.postInstagramImage(eq("1784"), anyString(), anyString(), any()))
                .thenReturn(new PublishedPost("ig-5", null));

        JsonNode result = send(post("/api/admin/events/" + eventId + "/share"), owner, body(true, true), 200);
        assertThat(result.at("/posts/0/platform").asText()).isEqualTo("FACEBOOK");
        assertThat(result.at("/posts/0/status").asText()).isEqualTo("FAILED");
        assertThat(result.at("/posts/0/error").asText()).contains("click Reconnect on Integrations");
        assertThat(result.at("/posts/1/status").asText()).isEqualTo("POSTED");
        assertThat(send(get("/api/admin/events"), owner, null, 200).get(0).get("sharedOn").toString())
                .isEqualTo("[\"INSTAGRAM\"]");
    }

    @Test
    void only_the_owner_shares_and_only_their_own_events() throws Exception {
        String other = registerAndLogin("owner@other.test", "Other Bar", "RESTAURANT");
        send(get("/api/admin/events/" + eventId + "/share"), other, null, 404);
        send(post("/api/admin/events/" + eventId + "/share"), customerToken("guest@example.com"),
                body(true, false), 403);
    }

    // --- helpers -----------------------------------------------------------

    private static String body(boolean facebook, boolean instagram) {
        return """
                {"facebook":%s,"instagram":%s,"caption":"Come along!"}""".formatted(facebook, instagram);
    }

    private void connect(MetaPage page) throws Exception {
        when(graph.exchangeCode(anyString(), anyString())).thenReturn("short");
        when(graph.longLivedUserToken("short")).thenReturn("long");
        when(graph.pages("long")).thenReturn(List.of(page));
        String url = send(post("/api/admin/integrations/meta/start"), owner, null, 200).get("authorizeUrl").asText();
        String state = URLDecoder.decode(url.replaceAll(".*[?&]state=([^&]+).*", "$1"), StandardCharsets.UTF_8);
        send(post("/api/admin/integrations/meta/complete"), owner, """
                {"code":"c","state":"%s"}""".formatted(state), 200);
    }
}
