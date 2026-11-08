package com.bookingsaas.site.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * One block of a business's homepage: its type (the JSON {@code type}), the
 * layout it is drawn with, where it shows and what it says.
 *
 * <p>Each type has its own record, so its layouts are an enum of that type
 * only and its content is checked field by field: a layout of another type,
 * or an unknown type, does not even parse. Texts left null are filled in by
 * the page (the business's name, tagline or description, or a default
 * heading), so renaming the business never leaves the page behind.
 *
 * <p>Booking data — services, employees, opening hours, events — is never
 * copied in: sections hold ids, and the page reads the live data.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Section.Hero.class, name = "HERO"),
        @JsonSubTypes.Type(value = Section.Events.class, name = "EVENTS"),
        @JsonSubTypes.Type(value = Section.Services.class, name = "SERVICES"),
        @JsonSubTypes.Type(value = Section.About.class, name = "ABOUT"),
        @JsonSubTypes.Type(value = Section.Gallery.class, name = "GALLERY"),
        @JsonSubTypes.Type(value = Section.Testimonials.class, name = "TESTIMONIALS"),
        @JsonSubTypes.Type(value = Section.Team.class, name = "TEAM"),
        @JsonSubTypes.Type(value = Section.Pricing.class, name = "PRICING"),
        @JsonSubTypes.Type(value = Section.Contact.class, name = "CONTACT")
})
// The same names for the API description, which would otherwise call each type by its schema name.
@Schema(discriminatorProperty = "type", discriminatorMapping = {
        @DiscriminatorMapping(value = "HERO", schema = Section.Hero.class),
        @DiscriminatorMapping(value = "EVENTS", schema = Section.Events.class),
        @DiscriminatorMapping(value = "SERVICES", schema = Section.Services.class),
        @DiscriminatorMapping(value = "ABOUT", schema = Section.About.class),
        @DiscriminatorMapping(value = "GALLERY", schema = Section.Gallery.class),
        @DiscriminatorMapping(value = "TESTIMONIALS", schema = Section.Testimonials.class),
        @DiscriminatorMapping(value = "TEAM", schema = Section.Team.class),
        @DiscriminatorMapping(value = "PRICING", schema = Section.Pricing.class),
        @DiscriminatorMapping(value = "CONTACT", schema = Section.Contact.class)
})
public sealed interface Section {

    String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    /** Stable id: React key, page anchor ({@code #section-<id>}) and target of "scroll to" buttons. */
    String id();

    Visibility visibility();

    Style style();

    /** The buttons the section shows; links are checked by the validator. */
    @JsonIgnore
    default List<Button> buttons() {
        return List.of();
    }

    /** Every image the section points at, so the validator can check they are the business's own. */
    @JsonIgnore
    default List<Image> images() {
        return List.of();
    }

    // --- shared parts --------------------------------------------------------

    /** On which screens the section shows. Both false hides it without losing its settings. */
    record Visibility(boolean desktop, boolean mobile) {

        public static final Visibility ALWAYS = new Visibility(true, true);
        public static final Visibility HIDDEN = new Visibility(false, false);
    }

    /** How the section sits on the page. Backgrounds, spacing and widths join here in later phases. */
    record Style(Tone tone) {

        public static final Style DEFAULT = new Style(Tone.DEFAULT);
        public static final Style SURFACE = new Style(Tone.SURFACE);
    }

    /**
     * Which theme colour the section is drawn on: the page background, the
     * card surface (the alternating band), the primary colour or the
     * secondary (dark) colour.
     */
    enum Tone { DEFAULT, SURFACE, PRIMARY, SECONDARY }

    /** An uploaded image of the business, with its description for screen readers. */
    record Image(@NotNull UUID mediaId, @Size(max = 200) String alt) {
    }

    /** What a button does: open the booking flow, scroll to a section, or follow a link. */
    enum ButtonAction { BOOKING, SECTION, URL }

    /**
     * @param target the section id for {@code SECTION}, the address for {@code URL}
     *               (http(s), mailto: or tel:), unused for {@code BOOKING}
     */
    record Button(@NotBlank @Size(max = 40) String label, @NotNull ButtonAction action,
                  @Size(max = 500) String target) {
    }

    /** Which services a list shows: all bookable ones, live, or the ones picked, in that order. */
    enum ServiceSource { ALL, SELECTED }

    // --- section types -------------------------------------------------------

    /**
     * The opening banner. Title null shows the business name, subtitle null its
     * tagline, no images its cover photo, and buttons null the usual booking button.
     */
    record Hero(@NotNull @Pattern(regexp = UUID_PATTERN) String id, @NotNull HeroLayout layout,
                @NotNull @Valid Visibility visibility, @NotNull @Valid Style style,
                @NotNull @Valid Content content) implements Section {

        public record Content(@Size(max = 60) String eyebrow, @Size(max = 120) String title,
                              @Size(max = 300) String subtitle,
                              @Size(max = 8) List<@NotNull @Valid Image> images,
                              /* YouTube or Vimeo link, for the VIDEO layout. */
                              @Size(max = 300) String videoUrl,
                              @Size(max = 3) List<@NotNull @Valid Button> buttons) {
        }

        @Override
        public List<Button> buttons() {
            return content.buttons() == null ? List.of() : content.buttons();
        }

        @Override
        public List<Image> images() {
            return content.images() == null ? List.of() : content.images();
        }
    }

    enum HeroLayout { FULL_WIDTH_IMAGE, SPLIT, CENTERED, VIDEO, SLIDESHOW }

    /** The business's events showing now, read live. Shows nothing when there are none. */
    record Events(@NotNull @Pattern(regexp = UUID_PATTERN) String id, @NotNull EventsLayout layout,
                  @NotNull @Valid Visibility visibility, @NotNull @Valid Style style,
                  @NotNull @Valid Content content) implements Section {

        public record Content(@Size(max = 60) String eyebrow, @Size(max = 120) String title) {
        }
    }

    enum EventsLayout { CARDS, LIST }

    /** The bookable services, read live, each linking into the booking flow. */
    record Services(@NotNull @Pattern(regexp = UUID_PATTERN) String id, @NotNull ServicesLayout layout,
                    @NotNull @Valid Visibility visibility, @NotNull @Valid Style style,
                    @NotNull @Valid Content content) implements Section {

        public record Content(@Size(max = 60) String eyebrow, @Size(max = 120) String title,
                              @NotNull ServiceSource source,
                              @Size(max = 50) List<@NotNull Long> serviceIds,
                              /* The "Book" button under the list. */
                              boolean showBookButton) {
        }
    }

    /** {@code GROUPED_LIST} is the price list by category the page has always had. */
    enum ServicesLayout { GROUPED_LIST, GRID, LIST, CAROUSEL, ALTERNATING }

    /** Who the business is. Text null shows the business description, title null "Welcome to …". */
    record About(@NotNull @Pattern(regexp = UUID_PATTERN) String id, @NotNull AboutLayout layout,
                 @NotNull @Valid Visibility visibility, @NotNull @Valid Style style,
                 @NotNull @Valid Content content) implements Section {

        public record Content(@Size(max = 60) String eyebrow, @Size(max = 120) String title,
                              @Size(max = 4000) String text, @Valid Image image,
                              @Size(max = 4) List<@NotNull @Valid Stat> stats) {
        }

        /** "15+" / "years of experience". */
        public record Stat(@NotBlank @Size(max = 20) String value, @NotBlank @Size(max = 60) String label) {
        }

        @Override
        public List<Image> images() {
            return content.image() == null ? List.of() : List.of(content.image());
        }
    }

    enum AboutLayout { CENTERED_TEXT, TEXT_IMAGE, TEXT_STATS }

    /** Photos: the business's gallery (managed in Settings, read live) or images picked for this section. */
    record Gallery(@NotNull @Pattern(regexp = UUID_PATTERN) String id, @NotNull GalleryLayout layout,
                   @NotNull @Valid Visibility visibility, @NotNull @Valid Style style,
                   @NotNull @Valid Content content) implements Section {

        public record Content(@Size(max = 60) String eyebrow, @Size(max = 120) String title,
                              @NotNull GallerySource source,
                              @Size(max = 24) List<@NotNull @Valid Image> images) {
        }

        @Override
        public List<Image> images() {
            return content.images() == null ? List.of() : content.images();
        }
    }

    enum GallerySource { GALLERY, CUSTOM }

    enum GalleryLayout { GRID, MASONRY, CAROUSEL }

    /** What customers say. The platform has no reviews, so the business writes them in here. */
    record Testimonials(@NotNull @Pattern(regexp = UUID_PATTERN) String id, @NotNull TestimonialsLayout layout,
                        @NotNull @Valid Visibility visibility, @NotNull @Valid Style style,
                        @NotNull @Valid Content content) implements Section {

        public record Content(@Size(max = 60) String eyebrow, @Size(max = 120) String title,
                              @Size(max = 12) List<@NotNull @Valid Quote> items) {
        }

        public record Quote(@NotBlank @Size(max = 600) String quote, @NotBlank @Size(max = 80) String author,
                            @Size(max = 80) String detail, @Min(1) @Max(5) Integer rating, @Valid Image photo) {
        }

        @Override
        public List<Image> images() {
            return content.items() == null ? List.of() : content.items().stream()
                    .map(Quote::photo).filter(java.util.Objects::nonNull).toList();
        }
    }

    enum TestimonialsLayout { CARDS, SLIDER, FEATURED }

    /**
     * The people, picked from the business's employees and shown with their
     * live name and photo. Only employees picked here are ever public.
     */
    record Team(@NotNull @Pattern(regexp = UUID_PATTERN) String id, @NotNull TeamLayout layout,
                @NotNull @Valid Visibility visibility, @NotNull @Valid Style style,
                @NotNull @Valid Content content) implements Section {

        public record Content(@Size(max = 60) String eyebrow, @Size(max = 120) String title,
                              @Size(max = 30) List<@NotNull @Valid Member> members) {
        }

        /** Role and bio are written here: an employee record has neither. */
        public record Member(@NotNull Long employeeId, @Size(max = 80) String role, @Size(max = 400) String bio) {
        }
    }

    enum TeamLayout { CARDS, LIST }

    /** Prices of the services, read live. One can be highlighted. */
    record Pricing(@NotNull @Pattern(regexp = UUID_PATTERN) String id, @NotNull PricingLayout layout,
                   @NotNull @Valid Visibility visibility, @NotNull @Valid Style style,
                   @NotNull @Valid Content content) implements Section {

        public record Content(@Size(max = 60) String eyebrow, @Size(max = 120) String title,
                              @NotNull ServiceSource source,
                              @Size(max = 50) List<@NotNull Long> serviceIds,
                              Long highlightServiceId) {
        }
    }

    enum PricingLayout { CARDS, TABLE }

    /** Address, phone and opening hours, read live from the business. */
    record Contact(@NotNull @Pattern(regexp = UUID_PATTERN) String id, @NotNull ContactLayout layout,
                   @NotNull @Valid Visibility visibility, @NotNull @Valid Style style,
                   @NotNull @Valid Content content) implements Section {

        public record Content(@Size(max = 60) String eyebrow, @Size(max = 120) String title, boolean showHours) {
        }
    }

    /** {@code MAP_AND_INFO} is the "How to find us" block the page has always had. */
    enum ContactLayout { MAP_AND_INFO, INFO_ONLY, WITH_HOURS }
}
