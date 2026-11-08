package com.bookingsaas.site;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessType;
import com.bookingsaas.site.model.Section;
import com.bookingsaas.site.model.SiteDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.bookingsaas.site.SiteFixtures.business;
import static org.assertj.core.api.Assertions.assertThat;

/** A business that never opened the builder keeps the page it had before it existed. */
@DisplayName("default homepage")
class SiteDefaultsTest {

    @Test
    void the_page_keeps_the_order_it_always_had() {
        SiteDocument page = SiteDefaults.forBusiness(business(BusinessType.RESTAURANT), true);

        assertThat(page.sections()).extracting(section -> section.getClass().getSimpleName())
                .containsExactly("Hero", "Events", "Services", "Gallery", "About", "Contact");
        assertThat(page.sections().get(0)).isInstanceOfSatisfying(Section.Hero.class,
                hero -> assertThat(hero.layout()).isEqualTo(Section.HeroLayout.FULL_WIDTH_IMAGE));
        assertThat(page.sections().get(2)).isInstanceOfSatisfying(Section.Services.class,
                services -> assertThat(services.layout()).isEqualTo(Section.ServicesLayout.GROUPED_LIST));
        assertThat(page.header().transparentOverHero()).isTrue();
    }

    @Test
    void texts_are_left_to_the_business_profile() {
        Section.Hero hero = (Section.Hero) SiteDefaults.forBusiness(business(BusinessType.SPA), false).sections().get(0);

        assertThat(hero.content().title()).isNull();
        assertThat(hero.content().subtitle()).isNull();
        assertThat(hero.content().images()).isEmpty();
        assertThat(hero.content().buttons()).isNull();
    }

    @Test
    void the_brand_colour_and_black_background_carry_over() {
        Business business = business(BusinessType.SPA);
        business.setDarkTheme(true);

        SiteDocument.Colors colors = SiteDefaults.forBusiness(business, false).theme().colors();

        assertThat(colors.primary()).isEqualTo("#b4536a");
        assertThat(colors.background()).isEqualTo("#000000");
        assertThat(colors.dark()).isTrue();
        assertThat(SiteDefaults.forBusiness(business(BusinessType.SPA), false).theme().colors().dark()).isFalse();
    }

    @Test
    void the_map_and_gallery_switches_carry_over() {
        Business business = business(BusinessType.SPA);
        business.setShowMap(false);
        business.setShowGallery(false);

        SiteDocument page = SiteDefaults.forBusiness(business, true);

        Section.Contact contact = (Section.Contact) page.sections().get(page.sections().size() - 1);
        assertThat(contact.layout()).isEqualTo(Section.ContactLayout.INFO_ONLY);
        assertThat(page.sections()).filteredOn(Section.Gallery.class::isInstance).singleElement()
                .satisfies(gallery -> assertThat(gallery.visibility()).isEqualTo(Section.Visibility.HIDDEN));
    }

    @Test
    void only_a_business_with_events_gets_an_events_section() {
        assertThat(SiteDefaults.forBusiness(business(BusinessType.HAIR_SALON), false).sections())
                .noneMatch(Section.Events.class::isInstance);
        // From before business types: it kept its events, so it keeps the section.
        assertThat(SiteDefaults.forBusiness(business(null), false).sections())
                .anyMatch(Section.Events.class::isInstance);
    }

    @Test
    void the_default_page_is_the_same_page_every_time() {
        assertThat(SiteDefaults.forBusiness(business(BusinessType.SPA), true))
                .isEqualTo(SiteDefaults.forBusiness(business(BusinessType.SPA), true));
    }
}
