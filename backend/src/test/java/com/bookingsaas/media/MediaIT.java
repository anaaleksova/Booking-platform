package com.bookingsaas.media;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Image uploads and the customer-facing booking page: only real images are
 * accepted, a business can only attach its own images, and the public pages
 * show one business at a time.
 */
@DisplayName("media and booking page")
class MediaIT extends AbstractIntegrationTest {

    /** A valid 1×1 PNG. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    private String token;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        token = registerAndLogin("owner@media.test", "Photo Studio");
        slug = slugOf(token);
    }

    @Test
    void an_uploaded_image_can_be_attached_and_is_served_publicly() throws Exception {
        JsonNode uploaded = upload(token, "logo.png", PNG, 201);
        String id = uploaded.get("id").asText();

        JsonNode service = send(post("/api/admin/services"), token, """
                {"name":"Portrait","category":"Photos","imageMediaId":"%s","durationMinutes":60,"price":40,
                 "pricePerParticipant":false,"maxParticipants":1,"bufferBeforeMinutes":0,"bufferAfterMinutes":0,
                 "allowCustomerResourceSelection":false,"active":true,"requirements":[]}
                """.formatted(id), 201);
        assertThat(service.get("imageUrl").asText()).isEqualTo("/api/public/media/" + id);

        MvcResult image = mockMvc.perform(get("/api/public/media/" + id)).andExpect(status().isOk()).andReturn();
        assertThat(image.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(image.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(image.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("immutable");

        JsonNode publicServices = send(get("/api/public/businesses/" + slug + "/services"), null, null, 200);
        assertThat(publicServices.get(0).get("category").asText()).isEqualTo("Photos");
        assertThat(publicServices.get(0).get("imageUrl").asText()).endsWith(id);
    }

    @Test
    void a_file_that_is_not_an_image_is_refused_whatever_it_claims_to_be() throws Exception {
        byte[] script = "<script>alert(1)</script>".getBytes();
        JsonNode error = upload(token, "photo.png", script, 400);

        assertThat(error.get("message").asText()).contains("JPEG, PNG, WebP or GIF");
    }

    @Test
    void the_booking_page_shows_branding_and_replaced_images_are_deleted() throws Exception {
        String first = upload(token, "cover.png", PNG, 201).get("id").asText();
        updateBusiness(token, first);
        String second = upload(token, "cover2.png", PNG, 201).get("id").asText();
        updateBusiness(token, second);

        JsonNode page = send(get("/api/public/businesses/" + slug), null, null, 200);
        assertThat(page.get("tagline").asText()).isEqualTo("Light, timing, you.");
        assertThat(page.get("accentColor").asText()).isEqualTo("#aa3355");
        assertThat(page.get("coverUrl").asText()).endsWith(second);

        // The replaced cover no longer exists.
        send(get("/api/public/media/" + first), null, null, 404);
    }

    @Test
    void a_business_cannot_attach_another_businesss_image() throws Exception {
        String otherToken = registerAndLogin("other@media.test", "Other Studio");
        String foreign = upload(otherToken, "theirs.png", PNG, 201).get("id").asText();

        send(put("/api/admin/business"), token, businessBody(foreign), 400);
    }

    @Test
    void there_is_no_public_list_of_all_businesses() throws Exception {
        send(get("/api/public/businesses"), null, null, 404);
    }

    // --- helpers -----------------------------------------------------------

    private JsonNode upload(String bearerToken, String name, byte[] bytes, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", name, "image/png", bytes))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json(result.getResponse().getContentAsString());
    }

    private void updateBusiness(String bearerToken, String coverId) throws Exception {
        send(put("/api/admin/business"), bearerToken, businessBody(coverId), 200);
    }

    private String businessBody(String coverId) {
        return """
                {"name":"Photo Studio","slug":"%s","currency":"EUR","timezone":"Europe/Skopje",
                 "tagline":"Light, timing, you.","accentColor":"#AA3355","coverMediaId":"%s","showMap":true}
                """.formatted(slug, coverId);
    }
}
