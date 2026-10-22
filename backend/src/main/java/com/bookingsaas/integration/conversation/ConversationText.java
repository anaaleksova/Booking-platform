package com.bookingsaas.integration.conversation;

import com.bookingsaas.integration.api.IntegrationDtos.NamedResource;
import com.bookingsaas.integration.api.IntegrationDtos.ResourceChoice;
import com.bookingsaas.integration.api.IntegrationDtos.ServiceInfo;
import com.bookingsaas.integration.conversation.ConversationData.Offer;
import com.bookingsaas.integration.conversation.ConversationDtos.Option;
import com.bookingsaas.integration.conversation.ConversationEnums.TimeOfDay;
import com.bookingsaas.reservation.Reservation;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Pure helpers shared by the conversation flows: labels, numbering, name matching. */
final class ConversationText {

    static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private ConversationText() {
    }

    static ServiceInfo find(List<ServiceInfo> services, Long id) {
        return services.stream().filter(service -> service.id().equals(id)).findFirst().orElse(null);
    }

    static List<Option> options(ConversationData d) {
        return d.getOffers().stream().map(offer -> new Option(offer.number(), offer.label(), offer.value())).toList();
    }

    static List<Offer> numbered(List<Offer> offers) {
        return IntStream.range(0, offers.size())
                .mapToObj(i -> new Offer(i + 1, offers.get(i).label(), offers.get(i).value()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** At most {@code max} slots, spread over the day rather than the first few. */
    static <T> List<T> spread(List<T> items, int max) {
        if (items.size() <= max) {
            return items;
        }
        return IntStream.range(0, max)
                .map(i -> (int) Math.round(i * (items.size() - 1) / (double) (max - 1)))
                .distinct()
                .mapToObj(items::get)
                .toList();
    }

    static boolean within(LocalTime time, TimeOfDay part) {
        return switch (part) {
            case MORNING -> time.isBefore(LocalTime.NOON);
            case AFTERNOON -> !time.isBefore(LocalTime.NOON) && time.isBefore(LocalTime.of(17, 0));
            case EVENING -> !time.isBefore(LocalTime.of(17, 0));
        };
    }

    static String serviceLabel(ServiceInfo service) {
        String price = service.price() == null ? "" : " · " + service.price().stripTrailingZeros().toPlainString()
                + " " + service.currency() + (service.pricePerParticipant() ? " per person" : "");
        return service.name() + " · " + service.durationMinutes() + " min" + price;
    }

    /** What a customer may ask for by name: for one service, or for every service while none is chosen. */
    static List<String> resourceNames(List<ServiceInfo> services, Long serviceId) {
        return services.stream()
                .filter(service -> serviceId == null || service.id().equals(serviceId))
                .flatMap(service -> service.resourceSelection().stream())
                .flatMap(requirement -> requirement.resources().stream())
                .map(NamedResource::name)
                .distinct()
                .toList();
    }

    static List<String> requirementResourceNames(ServiceInfo service, Long requirementId) {
        return service.resourceSelection().stream()
                .filter(requirement -> requirement.requirementId().equals(requirementId))
                .flatMap(requirement -> requirement.resources().stream())
                .map(NamedResource::name)
                .toList();
    }

    /** The offered choice holding the resource the customer named; an exact name wins over a partial one. */
    static ResourceChoice matchChoice(List<ResourceChoice> choices, String wanted) {
        String name = normalizeName(wanted);
        if (name.isEmpty()) {
            return null;
        }
        return choices.stream()
                .filter(choice -> choice.resources().stream().anyMatch(r -> normalizeName(r.name()).equals(name)))
                .findFirst()
                .or(() -> choices.stream()
                        .filter(choice -> choice.resources().stream()
                                .anyMatch(r -> partialName(normalizeName(r.name()), name)))
                        .findFirst())
                .orElse(null);
    }

    static boolean mentions(List<String> names, String wanted) {
        String name = normalizeName(wanted);
        return names.stream().map(ConversationText::normalizeName)
                .anyMatch(candidate -> candidate.equals(name) || partialName(candidate, name));
    }

    private static boolean partialName(String candidate, String wanted) {
        return candidate.length() >= 3 && wanted.length() >= 3
                && (candidate.contains(wanted) || wanted.contains(candidate));
    }

    private static String normalizeName(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    static String choiceLabel(ResourceChoice choice) {
        String names = choice.resources().stream().map(r -> r.name()).collect(Collectors.joining(" + "));
        return choice.area() == null ? names : names + " (" + choice.area() + ")";
    }

    static List<Long> idsOf(ResourceChoice choice) {
        return choice.resources().stream().map(r -> r.id()).toList();
    }

    static List<Long> ids(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }

    static String reservationLabel(Reservation reservation, ZoneId zone, Map<Long, String> serviceNames) {
        LocalDateTime start = LocalDateTime.ofInstant(reservation.getStartTime(), zone);
        return serviceNames.getOrDefault(reservation.getServiceId(), "Reservation") + " · "
                + DAY_LABEL.format(start) + " " + start.toLocalTime().format(HH_MM);
    }

    static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
