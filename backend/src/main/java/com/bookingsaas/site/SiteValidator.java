package com.bookingsaas.site;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.site.model.Section;
import com.bookingsaas.site.model.SiteDocument;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules of a homepage document that field annotations cannot express:
 * they span fields, or decide what a link may do.
 *
 * <p>Runs after Bean Validation, so every field is already present and in
 * range here. The message names the section by position and type, which is
 * how the owner sees it in the editor.
 */
public final class SiteValidator {

    /** Links leave the page only to the web, an email or a phone call — never {@code javascript:} or {@code data:}. */
    private static final Pattern SAFE_LINK = Pattern.compile("^(https?://[^\\s]+|mailto:[^\\s]+|tel:[+0-9 ()-]+)$",
            Pattern.CASE_INSENSITIVE);

    /** Videos are embedded by the page from these two providers only; the link names the video. */
    private static final Pattern VIDEO_LINK = Pattern.compile(
            "^https://(www\\.)?(youtube\\.com/watch\\?v=[\\w-]{6,20}([&#].*)?|youtu\\.be/[\\w-]{6,20}(\\?.*)?"
                    + "|youtube\\.com/shorts/[\\w-]{6,20}|vimeo\\.com/\\d{4,12}(\\?.*)?)$");

    private SiteValidator() {
    }

    public static void check(SiteDocument document, int currentVersion) {
        if (document.schemaVersion() != currentVersion) {
            throw ApiException.validation("This page was made with an older editor. Reload the page and try again.");
        }
        SiteDocument.Typography typography = document.theme().typography();
        if (typography.headingWeight() % 100 != 0) {
            throw ApiException.validation("The heading weight must be in hundreds, from 300 to 900.");
        }
        if (typography.bodyFont().headingsOnly()) {
            throw ApiException.validation("That font is made for headings only; choose another font for the text.");
        }
        Set<String> ids = new HashSet<>();
        for (Section section : document.sections()) {
            if (!ids.add(section.id())) {
                throw ApiException.validation("Two sections have the same id " + section.id() + ".");
            }
        }
        for (int index = 0; index < document.sections().size(); index++) {
            Section section = document.sections().get(index);
            String where = "Section " + (index + 1) + " (" + typeName(section) + "): ";
            for (Section.Button button : section.buttons()) {
                checkButton(where, button, ids);
            }
            if (section instanceof Section.Hero hero) {
                String video = hero.content().videoUrl();
                if (video != null && !video.isBlank() && !VIDEO_LINK.matcher(video.trim()).matches()) {
                    throw ApiException.validation(where + "the video must be a YouTube or Vimeo link.");
                }
                if (hero.layout() == Section.HeroLayout.VIDEO && (video == null || video.isBlank())) {
                    throw ApiException.validation(where + "the video layout needs a YouTube or Vimeo link.");
                }
            }
        }
    }

    private static void checkButton(String where, Section.Button button, Set<String> sectionIds) {
        String target = button.target() == null ? "" : button.target().trim();
        switch (button.action()) {
            case BOOKING -> {
            }
            case SECTION -> {
                if (!sectionIds.contains(target)) {
                    throw ApiException.validation(where + "the button \"" + button.label()
                            + "\" scrolls to a section that is not on the page.");
                }
            }
            case URL -> {
                if (!SAFE_LINK.matcher(target).matches()) {
                    throw ApiException.validation(where + "the button \"" + button.label()
                            + "\" needs a link starting with https://, mailto: or tel:.");
                }
            }
        }
    }

    /** The JSON name of the section's type, e.g. {@code HERO}. */
    static String typeName(Section section) {
        return switch (section) {
            case Section.Hero ignored -> "HERO";
            case Section.Events ignored -> "EVENTS";
            case Section.Services ignored -> "SERVICES";
            case Section.About ignored -> "ABOUT";
            case Section.Gallery ignored -> "GALLERY";
            case Section.Testimonials ignored -> "TESTIMONIALS";
            case Section.Team ignored -> "TEAM";
            case Section.Pricing ignored -> "PRICING";
            case Section.Contact ignored -> "CONTACT";
        };
    }
}
