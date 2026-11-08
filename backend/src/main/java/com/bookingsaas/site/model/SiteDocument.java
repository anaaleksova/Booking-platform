package com.bookingsaas.site.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A business's homepage as data: the global theme, the header and footer, and
 * the sections in order. Stored as JSON, once as the draft the owner edits and
 * once as the published copy customers see.
 *
 * <p>{@code schemaVersion} is the format version; stored documents of an older
 * version are upgraded on read by {@code SiteSchema}, so old data never breaks.
 */
public record SiteDocument(
        @NotNull Integer schemaVersion,
        @NotNull @Valid Theme theme,
        @NotNull @Valid Header header,
        @NotNull @Valid Footer footer,
        @NotNull @Size(max = SiteDocument.MAX_SECTIONS) List<@NotNull @Valid Section> sections
) {

    public static final int MAX_SECTIONS = 30;

    private static final String COLOR = "^#[0-9a-fA-F]{6}$";
    private static final String COLOR_MESSAGE = "must be a colour like #2f5bea";

    /** Applies to every customer page of the business: the homepage, the booking flow and the reservation page. */
    public record Theme(@NotNull @Valid Colors colors, @NotNull @Valid Typography typography,
                        @NotNull @Valid Components components) {
    }

    /**
     * The fonts a business can choose: a curated list, served from the
     * platform itself (never a third-party font service). {@code SYSTEM} is
     * the device's own font, which every page used before fonts could be chosen.
     */
    public enum Font {
        SYSTEM, INTER, ROBOTO, OPEN_SANS, LATO, MONTSERRAT, POPPINS, NUNITO, WORK_SANS, RALEWAY,
        PLAYFAIR_DISPLAY, LORA, MERRIWEATHER, CORMORANT_GARAMOND, DM_SERIF_DISPLAY, OSWALD, BEBAS_NEUE;

        /** Capitals only: fine for headings, unreadable as running text. */
        public boolean headingsOnly() {
            return this == BEBAS_NEUE;
        }
    }

    public enum HeadingStyle { NORMAL, UPPERCASE, ITALIC }

    public enum Align { CENTER, LEFT }

    /**
     * @param baseSize      body text size in px; everything else scales with it
     * @param headingWeight 300 (light) to 900 (black), in hundreds
     * @param headingAlign  where section headings sit
     */
    public record Typography(@NotNull Font headingFont, @NotNull Font bodyFont,
                             @NotNull @Min(14) @Max(20) Integer baseSize,
                             @NotNull @Min(300) @Max(900) Integer headingWeight,
                             @NotNull HeadingStyle headingStyle, @NotNull Align headingAlign) {

        /** How every page looked before typography could be chosen. */
        public static final Typography DEFAULT = new Typography(Font.SYSTEM, Font.SYSTEM, 16, 800,
                HeadingStyle.NORMAL, Align.CENTER);
    }

    /** Corners of cards and photos. */
    public enum Corners { SHARP, SOFT, ROUNDED }

    public enum ButtonShape { SQUARE, ROUNDED, PILL }

    /** The main buttons: solid colour, an outline, or text only. */
    public enum ButtonStyle { FILLED, OUTLINE, GHOST }

    /** Cards: flat on the card colour, with a border, or lifted by a shadow. */
    public enum CardStyle { MINIMAL, BORDERED, SHADOW }

    /** Photos in sections; a circle is cropped square first. */
    public enum ImageShape { SQUARE, ROUNDED, CIRCLE }

    public enum Shadow { SOFT, STRONG }

    /**
     * @param borderWidth px, for bordered cards
     * @param shadow      depth of shadowed cards
     */
    public record Components(@NotNull Corners corners, @NotNull ButtonShape buttonShape,
                             @NotNull ButtonStyle buttonStyle, @NotNull CardStyle cardStyle,
                             @NotNull ImageShape imageShape, @NotNull @Min(1) @Max(3) Integer borderWidth,
                             @NotNull Shadow shadow) {

        /** How every page looked before these could be chosen. */
        public static final Components DEFAULT = new Components(Corners.ROUNDED, ButtonShape.PILL,
                ButtonStyle.FILLED, CardStyle.BORDERED, ImageShape.ROUNDED, 1, Shadow.SOFT);
    }

    /**
     * @param primary    buttons, links and highlights
     * @param secondary  dark bands such as the footer
     * @param accent     small details: eyebrows, ratings, the highlighted price
     * @param background the page
     * @param surface    cards and the alternating section band
     * @param text       body text; headings use it too
     */
    public record Colors(
            @NotNull @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String primary,
            @NotNull @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String secondary,
            @NotNull @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String accent,
            @NotNull @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String background,
            @NotNull @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String surface,
            @NotNull @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String text) {

        /** Whether the page is light text on a dark background, judged by the background's luminance. */
        public boolean dark() {
            int rgb = Integer.parseInt(background.substring(1), 16);
            double luminance = (0.2126 * ((rgb >> 16) & 0xff) + 0.7152 * ((rgb >> 8) & 0xff)
                    + 0.0722 * (rgb & 0xff)) / 255;
            return luminance < 0.4;
        }
    }

    /**
     * @param transparentOverHero the bar runs over the hero's image instead of above it
     */
    public record Header(@NotNull HeaderLayout layout, boolean sticky, boolean transparentOverHero) {
    }

    /** More header layouts arrive with header customisation. */
    public enum HeaderLayout { LOGO_LEFT_NAV_RIGHT }

    public record Footer(@NotNull FooterLayout layout) {
    }

    /** More footer layouts arrive with footer customisation. */
    public enum FooterLayout { CENTERED }
}
