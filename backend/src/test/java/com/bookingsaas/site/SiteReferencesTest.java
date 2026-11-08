package com.bookingsaas.site;

import com.bookingsaas.site.model.Section;
import com.bookingsaas.site.model.SiteDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.bookingsaas.site.SiteFixtures.defaults;
import static com.bookingsaas.site.SiteFixtures.newId;
import static com.bookingsaas.site.SiteFixtures.withSections;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("homepage references")
class SiteReferencesTest {

    private static final UUID OWN_IMAGE = UUID.randomUUID();
    private static final UUID FOREIGN_IMAGE = UUID.randomUUID();

    private static final SiteReferences.Owned OWNED = new SiteReferences.Owned(Set.of(OWN_IMAGE), Set.of(1L, 2L),
            Set.of(10L));

    private static Section.Image image(UUID id) {
        return new Section.Image(id, "Dining room");
    }

    private static SiteDocument page() {
        return withSections(defaults(), List.of(
                new Section.Hero(newId(), Section.HeroLayout.SLIDESHOW, Section.Visibility.ALWAYS,
                        Section.Style.DEFAULT, new Section.Hero.Content(null, null, null,
                        List.of(image(OWN_IMAGE), image(FOREIGN_IMAGE)), null, null)),
                new Section.Services(newId(), Section.ServicesLayout.GRID, Section.Visibility.ALWAYS,
                        Section.Style.DEFAULT, new Section.Services.Content(null, null, Section.ServiceSource.SELECTED,
                        List.of(2L, 99L, 1L), true)),
                new Section.Pricing(newId(), Section.PricingLayout.CARDS, Section.Visibility.ALWAYS,
                        Section.Style.DEFAULT, new Section.Pricing.Content(null, null, Section.ServiceSource.ALL,
                        List.of(), 99L)),
                new Section.Team(newId(), Section.TeamLayout.CARDS, Section.Visibility.ALWAYS, Section.Style.DEFAULT,
                        new Section.Team.Content(null, null, List.of(new Section.Team.Member(10L, "Chef", null),
                                new Section.Team.Member(77L, "Stranger", null)))),
                new Section.Testimonials(newId(), Section.TestimonialsLayout.CARDS, Section.Visibility.ALWAYS,
                        Section.Style.DEFAULT, new Section.Testimonials.Content(null, null, List.of(
                        new Section.Testimonials.Quote("Lovely", "Ana", null, 5, image(FOREIGN_IMAGE))))),
                new Section.About(newId(), Section.AboutLayout.TEXT_IMAGE, Section.Visibility.ALWAYS,
                        Section.Style.DEFAULT, new Section.About.Content(null, null, "Hi", image(FOREIGN_IMAGE),
                        List.of()))));
    }

    @Test
    void every_pointer_of_the_page_is_collected() {
        SiteDocument page = page();

        assertThat(SiteReferences.mediaIds(page)).containsExactlyInAnyOrder(OWN_IMAGE, FOREIGN_IMAGE);
        assertThat(SiteReferences.serviceIds(page)).containsExactlyInAnyOrder(1L, 2L, 99L);
        assertThat(SiteReferences.employeeIds(page)).containsExactlyInAnyOrder(10L, 77L);
    }

    @Test
    void pointers_to_what_the_business_does_not_own_are_taken_out_and_the_rest_keeps_its_order() {
        List<Section> sections = SiteReferences.retainOwned(page(), OWNED).sections();

        Section.Hero hero = (Section.Hero) sections.get(0);
        assertThat(hero.content().images()).extracting(Section.Image::mediaId).containsExactly(OWN_IMAGE);

        Section.Services services = (Section.Services) sections.get(1);
        assertThat(services.content().serviceIds()).containsExactly(2L, 1L);

        Section.Pricing pricing = (Section.Pricing) sections.get(2);
        assertThat(pricing.content().highlightServiceId()).isNull();

        Section.Team team = (Section.Team) sections.get(3);
        assertThat(team.content().members()).extracting(Section.Team.Member::employeeId).containsExactly(10L);

        Section.Testimonials testimonials = (Section.Testimonials) sections.get(4);
        assertThat(testimonials.content().items().get(0).photo()).isNull();
        assertThat(testimonials.content().items().get(0).quote()).isEqualTo("Lovely");

        Section.About about = (Section.About) sections.get(5);
        assertThat(about.content().image()).isNull();
        assertThat(about.content().text()).isEqualTo("Hi");
    }

    @Test
    void a_page_pointing_at_nothing_foreign_is_unchanged() {
        SiteDocument page = defaults();

        assertThat(SiteReferences.retainOwned(page, OWNED)).isEqualTo(page);
    }
}
