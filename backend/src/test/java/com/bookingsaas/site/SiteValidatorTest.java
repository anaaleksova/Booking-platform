package com.bookingsaas.site;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.site.model.Section;
import com.bookingsaas.site.model.SiteDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.bookingsaas.site.SiteFixtures.defaults;
import static com.bookingsaas.site.SiteFixtures.hero;
import static com.bookingsaas.site.SiteFixtures.heroWithButton;
import static com.bookingsaas.site.SiteFixtures.plus;
import static com.bookingsaas.site.SiteFixtures.withSections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("homepage validation")
class SiteValidatorTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private static void check(SiteDocument document) {
        SiteValidator.check(document, SiteSchema.CURRENT_VERSION);
    }

    private static Set<String> violations(SiteDocument document) {
        Set<String> paths = new java.util.TreeSet<>();
        for (ConstraintViolation<SiteDocument> violation : VALIDATOR.validate(document)) {
            paths.add(violation.getPropertyPath().toString());
        }
        return paths;
    }

    @Test
    void the_default_page_is_valid() {
        assertThat(violations(defaults())).isEmpty();
        assertThatCode(() -> check(defaults())).doesNotThrowAnyException();
    }

    @Test
    void two_sections_with_the_same_id_are_refused() {
        SiteDocument document = defaults();
        List<Section> sections = new ArrayList<>(document.sections());
        sections.add(sections.get(0));

        assertThatThrownBy(() -> check(withSections(document, sections)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("same id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,<script>alert(1)</script>", "//evil.example",
            "ftp://files.example", ""})
    void a_button_link_that_is_not_web_mail_or_phone_is_refused(String target) {
        assertThatThrownBy(() -> check(plus(defaults(), heroWithButton(Section.ButtonAction.URL, target))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("https://");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://instagram.com/harbor", "http://example.com", "mailto:hi@harbor.example",
            "tel:+389 70 123 456"})
    void web_mail_and_phone_links_are_accepted(String target) {
        assertThatCode(() -> check(plus(defaults(), heroWithButton(Section.ButtonAction.URL, target))))
                .doesNotThrowAnyException();
    }

    @Test
    void a_button_scrolls_only_to_a_section_on_the_page() {
        SiteDocument document = defaults();
        String services = document.sections().get(2).id();

        assertThatCode(() -> check(plus(document, heroWithButton(Section.ButtonAction.SECTION, services))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> check(plus(document, heroWithButton(Section.ButtonAction.SECTION,
                SiteFixtures.newId()))))
                .hasMessageContaining("not on the page");
    }

    @Test
    void videos_come_from_youtube_or_vimeo_only() {
        assertThatCode(() -> check(plus(defaults(),
                hero(Section.HeroLayout.VIDEO, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", null))))
                .doesNotThrowAnyException();
        assertThatCode(() -> check(plus(defaults(), hero(Section.HeroLayout.VIDEO, "https://vimeo.com/76979871", null))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> check(plus(defaults(),
                hero(Section.HeroLayout.VIDEO, "https://evil.example/video.mp4", null))))
                .hasMessageContaining("YouTube or Vimeo");
        assertThatThrownBy(() -> check(plus(defaults(), hero(Section.HeroLayout.VIDEO, null, null))))
                .hasMessageContaining("needs a YouTube or Vimeo link");
    }

    @Test
    void a_document_of_another_format_version_is_refused() {
        SiteDocument document = defaults();
        SiteDocument old = new SiteDocument(0, document.theme(), document.header(), document.footer(),
                document.sections());

        assertThatThrownBy(() -> check(old)).hasMessageContaining("older editor");
    }

    @Test
    void colours_must_be_hex() {
        SiteDocument document = defaults();
        SiteDocument.Colors colors = document.theme().colors();
        SiteDocument red = new SiteDocument(document.schemaVersion(), new SiteDocument.Theme(
                new SiteDocument.Colors("red", colors.secondary(), colors.accent(), colors.background(),
                        colors.surface(), colors.text()), document.theme().typography(), document.theme().components()),
                document.header(), document.footer(), document.sections());

        assertThat(violations(red)).containsExactly("theme.colors.primary");
    }

    private static SiteDocument withTypography(SiteDocument.Typography typography) {
        SiteDocument document = defaults();
        return new SiteDocument(document.schemaVersion(), new SiteDocument.Theme(document.theme().colors(), typography,
                document.theme().components()), document.header(), document.footer(), document.sections());
    }

    @Test
    void type_sizes_and_weights_stay_in_range() {
        SiteDocument tiny = withTypography(new SiteDocument.Typography(SiteDocument.Font.LORA, SiteDocument.Font.INTER,
                9, 1000, SiteDocument.HeadingStyle.NORMAL, SiteDocument.Align.LEFT));

        assertThat(violations(tiny)).containsExactlyInAnyOrder("theme.typography.baseSize", "theme.typography.headingWeight");
        assertThatThrownBy(() -> check(withTypography(new SiteDocument.Typography(SiteDocument.Font.LORA,
                SiteDocument.Font.INTER, 16, 650, SiteDocument.HeadingStyle.NORMAL, SiteDocument.Align.LEFT))))
                .hasMessageContaining("hundreds");
    }

    @Test
    void a_capitals_only_font_is_for_headings_not_text() {
        SiteDocument.Typography bebasHeadings = new SiteDocument.Typography(SiteDocument.Font.BEBAS_NEUE,
                SiteDocument.Font.INTER, 16, 400, SiteDocument.HeadingStyle.UPPERCASE, SiteDocument.Align.CENTER);
        assertThatCode(() -> check(withTypography(bebasHeadings))).doesNotThrowAnyException();

        SiteDocument.Typography bebasText = new SiteDocument.Typography(SiteDocument.Font.INTER,
                SiteDocument.Font.BEBAS_NEUE, 16, 700, SiteDocument.HeadingStyle.NORMAL, SiteDocument.Align.CENTER);
        assertThatThrownBy(() -> check(withTypography(bebasText))).hasMessageContaining("headings only");
    }

    @Test
    void a_font_not_on_the_list_does_not_parse() throws Exception {
        ObjectNode document = JSON.valueToTree(defaults());
        ((ObjectNode) document.at("/theme/typography")).put("headingFont", "Comic Sans");

        assertThatThrownBy(() -> JSON.treeToValue(document, SiteDocument.class)).hasMessageContaining("Comic Sans");
    }

    @Test
    void a_page_has_at_most_thirty_sections() {
        SiteDocument document = defaults();
        List<Section> many = new ArrayList<>(Collections.nCopies(SiteDocument.MAX_SECTIONS + 1,
                document.sections().get(0)));

        assertThat(violations(withSections(document, many))).contains("sections");
    }

    @Test
    void texts_and_lists_are_capped() {
        String longTitle = "x".repeat(121);
        Section.Hero hero = new Section.Hero(SiteFixtures.newId(), Section.HeroLayout.CENTERED,
                Section.Visibility.ALWAYS, Section.Style.DEFAULT,
                new Section.Hero.Content(null, longTitle, null, List.of(), null,
                        Collections.nCopies(4, new Section.Button("Book", Section.ButtonAction.BOOKING, null))));

        assertThat(violations(plus(defaults(), hero)))
                .containsExactlyInAnyOrder("sections[6].content.title", "sections[6].content.buttons");
    }

    @Test
    void an_unknown_section_type_or_a_layout_of_another_type_does_not_parse() throws Exception {
        ObjectNode valid = JSON.valueToTree(defaults());

        ObjectNode unknownType = valid.deepCopy();
        ((ObjectNode) unknownType.withArray("sections").get(0)).put("type", "SPACESHIP");
        assertThatThrownBy(() -> JSON.treeToValue(unknownType, SiteDocument.class))
                .hasMessageContaining("SPACESHIP");

        ObjectNode foreignLayout = valid.deepCopy();
        // MASONRY is a gallery layout, not a hero one.
        ((ObjectNode) foreignLayout.withArray("sections").get(0)).put("layout", "MASONRY");
        assertThatThrownBy(() -> JSON.treeToValue(foreignLayout, SiteDocument.class))
                .hasMessageContaining("MASONRY");
    }

    @Test
    void a_document_survives_a_round_trip_through_json() throws Exception {
        SiteDocument document = plus(defaults(), heroWithButton(Section.ButtonAction.URL, "https://example.com"));

        String json = JSON.writeValueAsString(document);

        assertThat(json).contains("\"type\":\"HERO\"");
        assertThat(JSON.readValue(json, SiteDocument.class)).isEqualTo(document);
    }
}
