package com.bookingsaas.integration.conversation;

import com.bookingsaas.integration.api.IntegrationDtos.ServiceInfo;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds the service a customer named — "a massage", "the couples one" — among
 * the business's services. Deliberately conservative: a single clear match is
 * taken, anything else becomes a question to the customer.
 */
final class ServiceMatcher {

    private static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "with", "book", "booking", "want",
            "like", "please", "one", "some", "session", "appointment", "reservation", "table");

    private ServiceMatcher() {
    }

    /** @return the best candidates, best first; one element means a confident match */
    static List<ServiceInfo> match(String requested, List<ServiceInfo> services) {
        String query = normalize(requested);
        if (query.isBlank()) {
            return List.of();
        }
        List<ServiceInfo> exact = services.stream().filter(service -> normalize(service.name()).equals(query)).toList();
        if (exact.size() == 1) {
            return exact;
        }

        Set<String> queryTokens = tokens(query);
        record Scored(ServiceInfo service, double score) {
        }
        List<Scored> scored = services.stream()
                .map(service -> new Scored(service, score(query, queryTokens, service)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .toList();
        if (scored.isEmpty()) {
            return List.of();
        }
        double best = scored.getFirst().score();
        // A clear winner is taken; a tie is the customer's call.
        if (scored.size() == 1 || scored.get(1).score() < best) {
            return List.of(scored.getFirst().service());
        }
        return scored.stream().filter(candidate -> candidate.score() == best).map(Scored::service).toList();
    }

    private static double score(String query, Set<String> queryTokens, ServiceInfo service) {
        String name = normalize(service.name());
        Set<String> nameTokens = tokens(name);
        Set<String> categoryTokens = tokens(normalize(service.category()));
        double score = 0;
        if (name.contains(query)) {
            score += 3;
        } else if (query.contains(name)) {
            score += 4;     // "a couples massage please" contains "couples massage"
        }
        for (String token : queryTokens) {
            if (nameTokens.contains(token) || nameTokens.stream().anyMatch(word -> sameStem(word, token))) {
                score += 1;
            } else if (categoryTokens.contains(token) || categoryTokens.stream().anyMatch(word -> sameStem(word, token))) {
                score += 0.5;
            }
        }
        return score;
    }

    /** "massages" ~ "massage", "couple" ~ "couples": enough stemming for service names. */
    private static boolean sameStem(String a, String b) {
        int length = Math.min(a.length(), b.length());
        return length >= 4 && a.regionMatches(0, b, 0, length) && Math.abs(a.length() - b.length()) <= 2;
    }

    private static Set<String> tokens(String text) {
        return Arrays.stream(text.split("\\s+"))
                .filter(token -> token.length() >= 3 && !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String folded = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
