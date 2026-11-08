package com.bookingsaas.site;

import com.bookingsaas.site.model.SiteDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("homepage format versions")
class SiteSchemaTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** A pretend v1 → v2 change: the hero "title" became "headline". */
    private static final UnaryOperator<ObjectNode> RENAME_TITLE = document -> {
        document.withArray("sections").forEach(section -> {
            ObjectNode content = (ObjectNode) section.get("content");
            if (content.has("title")) {
                content.set("headline", content.remove("title"));
            }
        });
        return document;
    };

    /** A pretend v2 → v3 change: every document gets a footer note. */
    private static final UnaryOperator<ObjectNode> ADD_NOTE = document -> document.put("note", "v3");

    private static ObjectNode parse(String json) throws Exception {
        return (ObjectNode) JSON.readTree(json);
    }

    @Test
    void a_current_document_is_returned_as_it_is() throws Exception {
        ObjectNode stored = parse("""
                {"schemaVersion":1,"sections":[{"content":{"title":"Hi"}}]}""");

        assertThat(new SiteSchema(List.of()).upgrade(stored)).isEqualTo(stored);
    }

    @Test
    void a_document_without_a_version_is_version_one() throws Exception {
        ObjectNode upgraded = new SiteSchema(List.of(ADD_NOTE)).upgrade(parse("{\"sections\":[]}"));

        assertThat(upgraded.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(upgraded.get("note").asText()).isEqualTo("v3");
    }

    @Test
    void a_version_one_page_gets_the_typography_and_styling_it_always_looked_like() throws Exception {
        ObjectNode upgraded = new SiteSchema().upgrade(parse("""
                {"schemaVersion":1,"theme":{"colors":{"primary":"#2f5bea"}},"sections":[]}"""));

        assertThat(upgraded.get("schemaVersion").asInt()).isEqualTo(SiteSchema.CURRENT_VERSION);
        assertThat(upgraded.at("/theme/colors/primary").asText()).isEqualTo("#2f5bea");
        assertThat(upgraded.at("/theme/typography/headingFont").asText()).isEqualTo("SYSTEM");
        assertThat(upgraded.at("/theme/typography/headingWeight").asInt()).isEqualTo(800);
        assertThat(upgraded.at("/theme/components/buttonShape").asText()).isEqualTo("PILL");
        assertThat(upgraded.at("/theme/components/cardStyle").asText()).isEqualTo("BORDERED");
    }

    @Test
    void a_stored_version_one_page_reads_as_a_complete_document() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ObjectNode v1 = json.valueToTree(SiteFixtures.defaults());
        v1.put("schemaVersion", 1);
        ((ObjectNode) v1.get("theme")).remove(List.of("typography", "components"));

        SiteDocument read = json.treeToValue(new SiteSchema().upgrade(v1), SiteDocument.class);

        assertThat(read).isEqualTo(SiteFixtures.defaults());
    }

    @Test
    void an_old_document_runs_through_every_step_it_is_behind_in_order() throws Exception {
        SiteSchema schema = new SiteSchema(List.of(RENAME_TITLE, ADD_NOTE));

        ObjectNode upgraded = schema.upgrade(parse("""
                {"schemaVersion":1,"sections":[{"content":{"title":"Hi"}}]}"""));

        assertThat(upgraded.get("schemaVersion").asInt()).isEqualTo(3);
        assertThat(upgraded.at("/sections/0/content/headline").asText()).isEqualTo("Hi");
        assertThat(upgraded.at("/sections/0/content").has("title")).isFalse();
        assertThat(upgraded.get("note").asText()).isEqualTo("v3");
    }

    @Test
    void a_document_part_way_up_only_runs_the_remaining_steps() throws Exception {
        SiteSchema schema = new SiteSchema(List.of(RENAME_TITLE, ADD_NOTE));

        ObjectNode upgraded = schema.upgrade(parse("""
                {"schemaVersion":2,"sections":[{"content":{"title":"Kept"}}]}"""));

        assertThat(upgraded.at("/sections/0/content/title").asText()).isEqualTo("Kept");
        assertThat(upgraded.get("note").asText()).isEqualTo("v3");
    }

    @Test
    void the_stored_document_is_not_changed() throws Exception {
        ObjectNode stored = parse("""
                {"schemaVersion":1,"sections":[{"content":{"title":"Hi"}}]}""");

        new SiteSchema(List.of(RENAME_TITLE)).upgrade(stored);

        assertThat(stored.at("/sections/0/content/title").asText()).isEqualTo("Hi");
    }

    @Test
    void a_document_from_a_newer_server_is_refused() throws Exception {
        assertThatThrownBy(() -> new SiteSchema().upgrade(parse("{\"schemaVersion\":99}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("newer");
    }
}
