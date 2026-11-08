package com.bookingsaas.site;

import com.bookingsaas.site.model.Section;
import com.bookingsaas.site.model.SiteDocument;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The images, services and employees a homepage points at, and the document
 * with every pointer that is not the business's own taken out.
 *
 * <p>Taken out rather than refused: a service deleted or a gallery photo
 * removed after it was put on the page must not make the page unsaveable.
 * An id of another business is dropped the same way, so it never reaches the
 * stored document.
 */
final class SiteReferences {

    /** What the business owns among the ids the document mentions. */
    record Owned(Set<UUID> mediaIds, Set<Long> serviceIds, Set<Long> employeeIds) {
    }

    private SiteReferences() {
    }

    static Set<UUID> mediaIds(SiteDocument document) {
        Set<UUID> ids = new LinkedHashSet<>();
        document.sections().forEach(section -> section.images().forEach(image -> ids.add(image.mediaId())));
        return ids;
    }

    static Set<Long> serviceIds(SiteDocument document) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Section section : document.sections()) {
            if (section instanceof Section.Services services) {
                ids.addAll(orEmpty(services.content().serviceIds()));
            } else if (section instanceof Section.Pricing pricing) {
                ids.addAll(orEmpty(pricing.content().serviceIds()));
                if (pricing.content().highlightServiceId() != null) {
                    ids.add(pricing.content().highlightServiceId());
                }
            }
        }
        return ids;
    }

    static Set<Long> employeeIds(SiteDocument document) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Section section : document.sections()) {
            if (section instanceof Section.Team team) {
                orEmpty(team.content().members()).forEach(member -> ids.add(member.employeeId()));
            }
        }
        return ids;
    }

    static SiteDocument retainOwned(SiteDocument document, Owned owned) {
        List<Section> sections = document.sections().stream().map(section -> retain(section, owned)).toList();
        return new SiteDocument(document.schemaVersion(), document.theme(), document.header(), document.footer(),
                sections);
    }

    private static Section retain(Section section, Owned owned) {
        Predicate<Section.Image> ownImage = image -> owned.mediaIds().contains(image.mediaId());
        return switch (section) {
            case Section.Hero hero -> {
                Section.Hero.Content c = hero.content();
                yield new Section.Hero(hero.id(), hero.layout(), hero.visibility(), hero.style(),
                        new Section.Hero.Content(c.eyebrow(), c.title(), c.subtitle(),
                                filter(c.images(), ownImage), c.videoUrl(), c.buttons()));
            }
            case Section.About about -> {
                Section.About.Content c = about.content();
                Section.Image image = c.image() != null && ownImage.test(c.image()) ? c.image() : null;
                yield new Section.About(about.id(), about.layout(), about.visibility(), about.style(),
                        new Section.About.Content(c.eyebrow(), c.title(), c.text(), image, c.stats()));
            }
            case Section.Gallery gallery -> {
                Section.Gallery.Content c = gallery.content();
                yield new Section.Gallery(gallery.id(), gallery.layout(), gallery.visibility(), gallery.style(),
                        new Section.Gallery.Content(c.eyebrow(), c.title(), c.source(),
                                filter(c.images(), ownImage)));
            }
            case Section.Testimonials testimonials -> {
                Section.Testimonials.Content c = testimonials.content();
                List<Section.Testimonials.Quote> items = c.items() == null ? null : c.items().stream()
                        .map(quote -> quote.photo() == null || ownImage.test(quote.photo()) ? quote
                                : new Section.Testimonials.Quote(quote.quote(), quote.author(), quote.detail(),
                                quote.rating(), null))
                        .toList();
                yield new Section.Testimonials(testimonials.id(), testimonials.layout(), testimonials.visibility(),
                        testimonials.style(), new Section.Testimonials.Content(c.eyebrow(), c.title(), items));
            }
            case Section.Services services -> {
                Section.Services.Content c = services.content();
                yield new Section.Services(services.id(), services.layout(), services.visibility(), services.style(),
                        new Section.Services.Content(c.eyebrow(), c.title(), c.source(),
                                filter(c.serviceIds(), owned.serviceIds()::contains), c.showBookButton()));
            }
            case Section.Pricing pricing -> {
                Section.Pricing.Content c = pricing.content();
                Long highlight = c.highlightServiceId() != null && owned.serviceIds().contains(c.highlightServiceId())
                        ? c.highlightServiceId() : null;
                yield new Section.Pricing(pricing.id(), pricing.layout(), pricing.visibility(), pricing.style(),
                        new Section.Pricing.Content(c.eyebrow(), c.title(), c.source(),
                                filter(c.serviceIds(), owned.serviceIds()::contains), highlight));
            }
            case Section.Team team -> {
                Section.Team.Content c = team.content();
                yield new Section.Team(team.id(), team.layout(), team.visibility(), team.style(),
                        new Section.Team.Content(c.eyebrow(), c.title(),
                                filter(c.members(), member -> owned.employeeIds().contains(member.employeeId()))));
            }
            case Section.Events events -> events;
            case Section.Contact contact -> contact;
        };
    }

    private static <T> List<T> filter(List<T> items, Predicate<T> keep) {
        return items == null ? null : items.stream().filter(Objects::nonNull).filter(keep).toList();
    }

    private static <T> Collection<T> orEmpty(Collection<T> items) {
        return items == null ? List.of() : items;
    }
}
