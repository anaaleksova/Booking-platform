package com.bookingsaas.site;

import com.bookingsaas.business.Business;
import com.bookingsaas.site.model.Section;
import com.bookingsaas.site.model.Section.Style;
import com.bookingsaas.site.model.Section.Visibility;
import com.bookingsaas.site.model.SiteDocument;
import com.bookingsaas.site.model.SiteDocument.Colors;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The homepage a business has before it ever opens the builder: exactly the
 * page every business had before there was one — hero over the cover photo,
 * events, the price list, gallery, about, how to find us — in its colour and
 * with its own switches (map, gallery, black background).
 *
 * <p>Texts are left null so the page keeps reading the business's name,
 * tagline and description live. Section ids are derived from the business
 * id, so the default page is the same page every time it is built.
 */
public final class SiteDefaults {

    static final Colors LIGHT = new Colors("#2f5bea", "#111214", "#2f5bea", "#fafafa", "#ffffff", "#131a2b");
    static final Colors DARK = new Colors("#2f5bea", "#111214", "#2f5bea", "#000000", "#121214", "#f4f5f7");

    private SiteDefaults() {
    }

    /**
     * @param hasGalleryPhotos the about band alternates with the gallery: on the
     *                         surface colour after photos, on the page colour without
     */
    public static SiteDocument forBusiness(Business business, boolean hasGalleryPhotos) {
        Colors base = business.isDarkTheme() ? DARK : LIGHT;
        String accent = business.getAccentColor();
        Colors colors = new Colors(accent, base.secondary(), accent, base.background(), base.surface(), base.text());

        Long id = business.getId();
        List<Section> sections = new ArrayList<>();
        sections.add(new Section.Hero(sectionId(id, "hero"), Section.HeroLayout.FULL_WIDTH_IMAGE,
                Visibility.ALWAYS, Style.DEFAULT,
                new Section.Hero.Content(null, null, null, List.of(), null, null)));
        if (business.offersEvents()) {
            sections.add(new Section.Events(sectionId(id, "events"), Section.EventsLayout.CARDS,
                    Visibility.ALWAYS, Style.DEFAULT, new Section.Events.Content(null, null)));
        }
        sections.add(new Section.Services(sectionId(id, "services"), Section.ServicesLayout.GROUPED_LIST,
                Visibility.ALWAYS, Style.SURFACE,
                new Section.Services.Content(null, null, Section.ServiceSource.ALL, List.of(), true)));
        sections.add(new Section.Gallery(sectionId(id, "gallery"), Section.GalleryLayout.GRID,
                business.isShowGallery() ? Visibility.ALWAYS : Visibility.HIDDEN, Style.DEFAULT,
                new Section.Gallery.Content(null, null, Section.GallerySource.GALLERY, List.of())));
        sections.add(new Section.About(sectionId(id, "about"), Section.AboutLayout.CENTERED_TEXT,
                Visibility.ALWAYS, hasGalleryPhotos && business.isShowGallery() ? Style.SURFACE : Style.DEFAULT,
                new Section.About.Content(null, null, null, null, List.of())));
        sections.add(new Section.Contact(sectionId(id, "contact"),
                business.isShowMap() ? Section.ContactLayout.MAP_AND_INFO : Section.ContactLayout.INFO_ONLY,
                Visibility.ALWAYS, Style.SURFACE, new Section.Contact.Content(null, null, true)));

        return new SiteDocument(SiteSchema.CURRENT_VERSION,
                new SiteDocument.Theme(colors, SiteDocument.Typography.DEFAULT, SiteDocument.Components.DEFAULT),
                new SiteDocument.Header(SiteDocument.HeaderLayout.LOGO_LEFT_NAV_RIGHT, true, true),
                new SiteDocument.Footer(SiteDocument.FooterLayout.CENTERED),
                sections);
    }

    private static String sectionId(Long businessId, String key) {
        return UUID.nameUUIDFromBytes(("site:" + businessId + ":" + key).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
