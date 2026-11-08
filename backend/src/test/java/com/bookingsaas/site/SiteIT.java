package com.bookingsaas.site;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** The homepage builder: a private draft, publishing it, throwing it away, and what customers see. */
@DisplayName("homepage builder")
class SiteIT extends AbstractIntegrationTest {

    private String token;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        token = registerAndLogin("owner@site.test", "Site Studio");
        slug = slugOf(token);
    }

    @Test
    void a_business_that_never_opened_the_builder_shows_the_page_it_always_had() throws Exception {
        JsonNode site = publicSite().get("site");

        assertThat(types(site)).containsExactly("HERO", "SERVICES", "GALLERY", "ABOUT", "CONTACT");
        assertThat(site.at("/sections/0/layout").asText()).isEqualTo("FULL_WIDTH_IMAGE");
        assertThat(site.at("/theme/colors/primary").asText()).isEqualTo("#2f5bea");
        assertThat(publicBusiness().at("/theme/colors/primary").asText()).isEqualTo("#2f5bea");
    }

    @Test
    void the_draft_stays_private_until_it_is_published() throws Exception {
        JsonNode site = send(get("/api/admin/site"), token, null, 200);
        assertThat(site.get("hasUnpublishedChanges").asBoolean()).isFalse();
        assertThat(site.hasNonNull("publishedAt")).isFalse();

        ObjectNode draft = (ObjectNode) site.get("draft");
        hero(draft).put("layout", "CENTERED");
        JsonNode saved = saveDraft(site.get("draftVersion").asInt(), draft, 200);

        assertThat(saved.get("hasUnpublishedChanges").asBoolean()).isTrue();
        assertThat(saved.at("/draft/sections/0/layout").asText()).isEqualTo("CENTERED");
        assertThat(publicSite().at("/site/sections/0/layout").asText()).isEqualTo("FULL_WIDTH_IMAGE");

        JsonNode published = send(post("/api/admin/site/publish"), token, null, 200);

        assertThat(published.get("hasUnpublishedChanges").asBoolean()).isFalse();
        assertThat(published.hasNonNull("publishedAt")).isTrue();
        assertThat(publicSite().at("/site/sections/0/layout").asText()).isEqualTo("CENTERED");
    }

    @Test
    void discarding_brings_the_draft_back_to_the_published_page() throws Exception {
        JsonNode site = send(get("/api/admin/site"), token, null, 200);
        ObjectNode draft = (ObjectNode) site.get("draft");
        ((ArrayNode) draft.get("sections")).remove(1);
        saveDraft(site.get("draftVersion").asInt(), draft, 200);

        JsonNode discarded = send(post("/api/admin/site/discard"), token, null, 200);

        assertThat(discarded.get("hasUnpublishedChanges").asBoolean()).isFalse();
        assertThat(discarded.get("draft")).isEqualTo(discarded.get("published"));
        assertThat(types(discarded.get("draft"))).contains("SERVICES");
    }

    @Test
    void a_save_from_an_editor_that_missed_a_later_save_is_refused() throws Exception {
        JsonNode site = send(get("/api/admin/site"), token, null, 200);
        int version = site.get("draftVersion").asInt();
        ObjectNode draft = (ObjectNode) site.get("draft");
        hero(draft).put("layout", "SPLIT");
        saveDraft(version, draft, 200);

        hero(draft).put("layout", "CENTERED");
        JsonNode refused = saveDraft(version, draft, 409);

        assertThat(refused.get("error").asText()).isEqualTo("SITE_DRAFT_OUTDATED");
    }

    @Test
    void publishing_carries_the_theme_to_the_booking_flow_and_emails() throws Exception {
        JsonNode site = send(get("/api/admin/site"), token, null, 200);
        ObjectNode draft = (ObjectNode) site.get("draft");
        ObjectNode colors = (ObjectNode) draft.at("/theme/colors");
        colors.put("primary", "#0F9D76").put("background", "#000000").put("surface", "#121214")
                .put("text", "#f4f5f7");
        saveDraft(site.get("draftVersion").asInt(), draft, 200);

        // Not before publishing.
        assertThat(send(get("/api/admin/business"), token, null, 200).get("accentColor").asText())
                .isEqualTo("#2f5bea");

        send(post("/api/admin/site/publish"), token, null, 200);

        JsonNode business = send(get("/api/admin/business"), token, null, 200);
        assertThat(business.get("accentColor").asText()).isEqualTo("#0f9d76");
        assertThat(business.get("darkTheme").asBoolean()).isTrue();
        assertThat(publicBusiness().at("/theme/colors/background").asText()).isEqualTo("#000000");
    }

    @Test
    void a_page_that_breaks_the_rules_is_not_saved() throws Exception {
        JsonNode site = send(get("/api/admin/site"), token, null, 200);
        int version = site.get("draftVersion").asInt();

        ObjectNode unsafeLink = site.get("draft").deepCopy();
        ArrayNode buttons = hero(unsafeLink).with("content").putArray("buttons");
        buttons.addObject().put("label", "Click").put("action", "URL").put("target", "javascript:alert(1)");
        assertThat(saveDraft(version, unsafeLink, 400).get("error").asText()).isEqualTo("VALIDATION_FAILED");

        ObjectNode unknownType = site.get("draft").deepCopy();
        hero(unknownType).put("type", "SPACESHIP");
        assertThat(saveDraft(version, unknownType, 400).get("error").asText()).isEqualTo("MALFORMED_REQUEST");

        ObjectNode badColour = site.get("draft").deepCopy();
        ((ObjectNode) badColour.at("/theme/colors")).put("text", "black");
        JsonNode refused = saveDraft(version, badColour, 400);
        assertThat(refused.get("fieldErrors").has("document.theme.colors.text")).isTrue();

        // Nothing was stored.
        assertThat(send(get("/api/admin/site"), token, null, 200).get("draftVersion").asInt()).isEqualTo(version);
    }

    @Test
    void a_team_section_makes_public_only_the_employees_it_picked() throws Exception {
        long marta = send(post("/api/admin/employees"), token, """
                {"name":"Marta","active":true,"capacity":1}""", 201).get("id").asLong();
        send(post("/api/admin/employees"), token, """
                {"name":"Not On The Page","active":true,"capacity":1}""", 201);
        JsonNode site = send(get("/api/admin/site"), token, null, 200);
        ObjectNode draft = (ObjectNode) site.get("draft");
        ObjectNode team = ((ArrayNode) draft.get("sections")).addObject();
        team.put("type", "TEAM").put("id", UUID.randomUUID().toString()).put("layout", "CARDS");
        team.putObject("visibility").put("desktop", true).put("mobile", true);
        team.putObject("style").put("tone", "DEFAULT");
        team.putObject("content").putArray("members").addObject().put("employeeId", marta).put("role", "Head chef");
        saveDraft(site.get("draftVersion").asInt(), draft, 200);

        assertThat(publicSite().get("team")).isEmpty();
        send(post("/api/admin/site/publish"), token, null, 200);

        JsonNode members = publicSite().get("team");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).get("name").asText()).isEqualTo("Marta");
    }

    @Test
    void the_gallery_section_reads_the_business_gallery_live() throws Exception {
        String photo = uploadImage(token);
        send(post("/api/admin/gallery"), token, "{\"mediaId\":\"" + photo + "\",\"caption\":\"Terrace\"}", 201);

        JsonNode gallery = publicSite().get("gallery");

        assertThat(gallery).hasSize(1);
        assertThat(gallery.get(0).get("caption").asText()).isEqualTo("Terrace");
    }

    @Test
    void a_page_stored_in_an_older_format_is_served_upgraded_and_saves_in_the_new_one() throws Exception {
        JsonNode current = send(get("/api/admin/site"), token, null, 200).get("draft");
        ObjectNode v1 = current.deepCopy();
        v1.put("schemaVersion", 1);
        ((ObjectNode) v1.get("theme")).remove(List.of("typography", "components"));
        jdbcTemplate.update("UPDATE site_config SET draft = ?::jsonb, published = ?::jsonb WHERE business_id = "
                + "(SELECT id FROM business WHERE slug = ?)", v1.toString(), v1.toString(), slug);

        JsonNode site = send(get("/api/admin/site"), token, null, 200);
        assertThat(site.at("/draft/schemaVersion").asInt()).isEqualTo(SiteSchema.CURRENT_VERSION);
        assertThat(site.at("/draft/theme/typography/bodyFont").asText()).isEqualTo("SYSTEM");
        assertThat(publicSite().at("/site/theme/components/buttonShape").asText()).isEqualTo("PILL");

        saveDraft(site.get("draftVersion").asInt(), site.get("draft"), 200);
        String stored = jdbcTemplate.queryForObject("SELECT draft::text FROM site_config WHERE business_id = "
                + "(SELECT id FROM business WHERE slug = ?)", String.class, slug);
        assertThat(objectMapper.readTree(stored).get("schemaVersion").asInt()).isEqualTo(SiteSchema.CURRENT_VERSION);
    }

    @Test
    void the_chosen_fonts_and_styles_reach_every_customer_page() throws Exception {
        JsonNode site = send(get("/api/admin/site"), token, null, 200);
        ObjectNode draft = (ObjectNode) site.get("draft");
        ((ObjectNode) draft.at("/theme/typography")).put("headingFont", "PLAYFAIR_DISPLAY").put("headingStyle", "ITALIC");
        ((ObjectNode) draft.at("/theme/components")).put("buttonStyle", "OUTLINE").put("corners", "SHARP");
        saveDraft(site.get("draftVersion").asInt(), draft, 200);
        send(post("/api/admin/site/publish"), token, null, 200);

        JsonNode theme = publicBusiness().get("theme");
        assertThat(theme.at("/typography/headingFont").asText()).isEqualTo("PLAYFAIR_DISPLAY");
        assertThat(theme.at("/components/buttonStyle").asText()).isEqualTo("OUTLINE");
        assertThat(theme.at("/components/corners").asText()).isEqualTo("SHARP");
    }

    // --- helpers -----------------------------------------------------------

    private JsonNode saveDraft(int version, JsonNode document, int status) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("draftVersion", version);
        body.set("document", document);
        return send(put("/api/admin/site/draft"), token, body.toString(), status);
    }

    private JsonNode publicSite() throws Exception {
        return send(get("/api/public/businesses/" + slug + "/site"), null, null, 200);
    }

    private JsonNode publicBusiness() throws Exception {
        return send(get("/api/public/businesses/" + slug), null, null, 200);
    }

    private static ObjectNode hero(JsonNode document) {
        return (ObjectNode) document.get("sections").get(0);
    }

    private static List<String> types(JsonNode document) {
        List<String> types = new ArrayList<>();
        document.get("sections").forEach(section -> types.add(section.get("type").asText()));
        return types;
    }
}
