package com.bookingsaas.business;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** Turns a business name into a URL-safe slug for {@code /book/{slug}}. */
public final class Slugs {

    private static final int MAX_LENGTH = 80;

    private Slugs() {
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")                 // strip accents: "Škopje" -> "Skopje"
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (normalized.length() > MAX_LENGTH) {
            normalized = normalized.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return normalized.isBlank() ? "business" : normalized;
    }

    /**
     * Appends {@code -2}, {@code -3} … until the slug is free. Uniqueness is
     * still enforced by the database; this only avoids the common collision.
     */
    public static String uniqueSlug(String desired, Predicate<String> taken) {
        String base = slugify(desired);
        if (!taken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = truncateForSuffix(base, suffix) + "-" + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not derive a unique slug from '" + desired + "'");
    }

    private static String truncateForSuffix(String base, int suffix) {
        int room = MAX_LENGTH - (String.valueOf(suffix).length() + 1);
        return base.length() <= room ? base : base.substring(0, room).replaceAll("-+$", "");
    }
}
