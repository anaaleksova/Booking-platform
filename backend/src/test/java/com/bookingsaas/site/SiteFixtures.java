package com.bookingsaas.site;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessType;
import com.bookingsaas.site.model.Section;
import com.bookingsaas.site.model.SiteDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small homepage documents for the site tests. */
final class SiteFixtures {

    private SiteFixtures() {
    }

    static Business business(BusinessType type) {
        Business business = new Business();
        business.setId(42L);
        business.setName("Harbor Bistro");
        business.setSlug("harbor-bistro");
        business.setAccentColor("#b4536a");
        business.setBusinessType(type);
        return business;
    }

    static SiteDocument defaults() {
        return SiteDefaults.forBusiness(business(BusinessType.RESTAURANT), true);
    }

    static SiteDocument withSections(SiteDocument document, List<Section> sections) {
        return new SiteDocument(document.schemaVersion(), document.theme(), document.header(), document.footer(),
                sections);
    }

    /** The document with one more section at the end. */
    static SiteDocument plus(SiteDocument document, Section section) {
        List<Section> sections = new ArrayList<>(document.sections());
        sections.add(section);
        return withSections(document, sections);
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    static Section.Hero hero(Section.HeroLayout layout, String videoUrl, List<Section.Button> buttons) {
        return new Section.Hero(newId(), layout, Section.Visibility.ALWAYS, Section.Style.DEFAULT,
                new Section.Hero.Content(null, "Welcome", null, List.of(), videoUrl, buttons));
    }

    static Section.Hero heroWithButton(Section.ButtonAction action, String target) {
        return hero(Section.HeroLayout.CENTERED, null, List.of(new Section.Button("Go", action, target)));
    }
}
