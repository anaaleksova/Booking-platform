package com.bookingsaas.business;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The optional booking-page gallery: ordering, captions, hiding, removal and the photo limit. */
@DisplayName("gallery")
class GalleryIT extends AbstractIntegrationTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    private String token;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        token = registerAndLogin("owner@gallery.test", "Gallery Studio");
        slug = slugOf(token);
    }

    @Test
    void photos_appear_on_the_booking_page_in_the_chosen_order() throws Exception {
        long first = addPhoto(token, "Front desk");
        long second = addPhoto(token, "Treatment room");
        long third = addPhoto(token, null);

        send(put("/api/admin/gallery/order"), token, "[" + third + "," + first + "," + second + "]", 200);
        send(put("/api/admin/gallery/" + third), token, "{\"caption\":\"Garden view\"}", 200);

        JsonNode gallery = publicGallery();
        assertThat(captions(gallery)).containsExactly("Garden view", "Front desk", "Treatment room");
        assertThat(gallery.get(0).get("url").asText()).startsWith("/api/public/media/");
    }

    @Test
    void a_hidden_gallery_keeps_its_photos_but_shows_nothing() throws Exception {
        addPhoto(token, "Front desk");
        send(put("/api/admin/business"), token, """
                {"name":"Gallery Studio","slug":"%s","currency":"EUR","timezone":"Europe/Skopje","showGallery":false}
                """.formatted(slug), 200);

        assertThat(publicGallery()).isEmpty();
        assertThat(send(get("/api/admin/gallery"), token, null, 200)).hasSize(1);
    }

    @Test
    void removing_a_photo_deletes_the_image() throws Exception {
        long id = addPhoto(token, "Old photo");
        String url = send(get("/api/admin/gallery"), token, null, 200).get(0).get("url").asText();

        send(delete("/api/admin/gallery/" + id), token, null, 204);

        assertThat(publicGallery()).isEmpty();
        send(get(url), null, null, 404);
    }

    @Test
    void the_gallery_holds_at_most_twelve_photos() throws Exception {
        for (int i = 0; i < 12; i++) {
            addPhoto(token, null);
        }
        String extra = upload(token);
        send(post("/api/admin/gallery"), token, "{\"mediaId\":\"" + extra + "\"}", 400);
    }

    @Test
    void a_reorder_must_list_every_photo_exactly_once() throws Exception {
        long first = addPhoto(token, null);
        addPhoto(token, null);

        send(put("/api/admin/gallery/order"), token, "[" + first + "]", 400);
    }

    @Test
    void another_businesss_image_cannot_be_added() throws Exception {
        String otherToken = registerAndLogin("other@gallery.test", "Other Studio");
        String foreign = upload(otherToken);

        send(post("/api/admin/gallery"), token, "{\"mediaId\":\"" + foreign + "\"}", 400);
    }

    // --- helpers -----------------------------------------------------------

    private long addPhoto(String bearerToken, String caption) throws Exception {
        String body = caption == null
                ? "{\"mediaId\":\"" + upload(bearerToken) + "\"}"
                : "{\"mediaId\":\"" + upload(bearerToken) + "\",\"caption\":\"" + caption + "\"}";
        return send(post("/api/admin/gallery"), bearerToken, body, 201).get("id").asLong();
    }

    private String upload(String bearerToken) throws Exception {
        String response = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(response).get("id").asText();
    }

    private JsonNode publicGallery() throws Exception {
        return send(get("/api/public/businesses/" + slug), null, null, 200).get("gallery");
    }

    private static List<String> captions(JsonNode gallery) {
        List<String> result = new ArrayList<>();
        gallery.forEach(photo -> result.add(photo.path("caption").asText(null)));
        return result;
    }
}
