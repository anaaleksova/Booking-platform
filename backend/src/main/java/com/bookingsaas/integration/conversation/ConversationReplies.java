package com.bookingsaas.integration.conversation;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.OpeningHoursService;
import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.event.EventService;
import com.bookingsaas.integration.api.IntegrationCatalogService;
import com.bookingsaas.integration.api.IntegrationDtos.Quote;
import com.bookingsaas.integration.api.IntegrationDtos.ReservationInfo;
import com.bookingsaas.integration.api.IntegrationDtos.ResourceOptions;
import com.bookingsaas.integration.api.IntegrationDtos.ServiceInfo;
import com.bookingsaas.integration.api.IntegrationDtos.Slot;
import com.bookingsaas.integration.conversation.ConversationData.Offer;
import com.bookingsaas.integration.conversation.ConversationDtos.Action;
import com.bookingsaas.integration.conversation.ConversationDtos.BusinessFacts;
import com.bookingsaas.integration.conversation.ConversationDtos.CustomerFacts;
import com.bookingsaas.integration.conversation.ConversationDtos.EventFact;
import com.bookingsaas.integration.conversation.ConversationDtos.ServiceFact;
import com.bookingsaas.integration.conversation.ConversationDtos.Summary;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnResponse;
import com.bookingsaas.integration.conversation.ConversationEnums.Ask;
import com.bookingsaas.integration.conversation.ConversationEnums.NextAction;
import com.bookingsaas.integration.conversation.ConversationEnums.Outcome;
import com.bookingsaas.integration.conversation.ConversationEnums.State;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.bookingsaas.integration.conversation.ConversationText.HH_MM;
import static com.bookingsaas.integration.conversation.ConversationText.choiceLabel;
import static com.bookingsaas.integration.conversation.ConversationText.hasText;
import static com.bookingsaas.integration.conversation.ConversationText.idsOf;
import static com.bookingsaas.integration.conversation.ConversationText.options;
import static com.bookingsaas.integration.conversation.ConversationText.resourceNames;

/**
 * Builds the answer to a turn: what to ask next, the facts for the reply,
 * and the business and customer details the model may quote.
 */
@Component
class ConversationReplies {

    /** How far ahead the chat knows about special opening hours. */
    private static final int SPECIAL_HOURS_DAYS = 30;
    private static final DateTimeFormatter SPECIAL_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final ConversationStore store;
    private final IntegrationCatalogService catalog;
    private final OpeningHoursService openingHoursService;
    private final EventService eventService;

    ConversationReplies(ConversationStore store, IntegrationCatalogService catalog,
                        OpeningHoursService openingHoursService, EventService eventService) {
        this.store = store;
        this.catalog = catalog;
        this.openingHoursService = openingHoursService;
        this.eventService = eventService;
    }

    /** Moves to {@code state}, remembers the question and the numbered offers, and asks it. */
    TurnResponse ask(ConversationTurn turn, Conversation c, ConversationData d, State state, Ask ask,
                     List<Offer> offers, List<String> missing, Summary summary) {
        c.setState(state);
        d.setAsked(ask);
        d.setOffers(new ArrayList<>(offers));
        if (ask != Ask.RESOURCE) {
            d.setAskedRequirementId(null);
        }
        store.save(c, d);
        return respond(turn, c, d, NextAction.REPLY, ask, missing, summary, null, null, null);
    }

    TurnResponse respond(ConversationTurn turn, Conversation c, ConversationData d, NextAction next, Ask ask,
                         List<String> missing, Summary summary, ReservationInfo reservation, Outcome outcome,
                         Action action) {
        return response(turn, c, d, next, ask, missing, summary, reservation, null, outcome, action);
    }

    TurnResponse response(ConversationTurn turn, Conversation c, ConversationData d, NextAction next, Ask ask,
                          List<String> missing, Summary summary, ReservationInfo reservation,
                          List<ReservationInfo> reservationList, Outcome outcome, Action action) {
        return new TurnResponse(c == null ? null : c.getId(), c == null ? null : c.getFlow().name(),
                c == null ? null : c.getState().name(), next, ask, d == null ? List.of() : options(d),
                List.copyOf(turn.problems()), missing, summary, reservation, reservationList, outcome, action,
                customerFacts(turn.linked()), businessFacts(turn.business()),
                d == null || d.getServiceId() == null ? List.of()
                        : resourceNames(catalog.services(turn.businessId()), d.getServiceId()));
    }

    TurnResponse ended(ConversationTurn turn, Conversation c, Outcome outcome, ReservationInfo reservation) {
        return response(turn, c, null, NextAction.REPLY, Ask.NOTHING, List.of(), null, reservation, null, outcome,
                null);
    }

    /** Nothing in progress: a greeting or a question. The reply introduces what the bot can do. */
    TurnResponse idle(ConversationTurn turn) {
        return response(turn, null, null, NextAction.REPLY, Ask.NOTHING, List.of(), null, null, null, null, null);
    }

    Summary summary(ConversationData d, ServiceInfo service, Slot slot, Quote quote, ZoneId zone) {
        List<String> resources = new ArrayList<>();
        if (slot != null) {
            for (ResourceOptions requirement : slot.resourceOptions()) {
                List<Long> selected = d.getResourceSelections().get(requirement.requirementId());
                if (selected == null || selected.isEmpty()) {
                    continue;
                }
                requirement.choices().stream().filter(choice -> idsOf(choice).equals(selected)).findFirst()
                        .ifPresent(choice -> resources.add(choiceLabel(choice)));
            }
        }
        String name = hasText(d.getFirstName())
                ? (d.getFirstName() + (hasText(d.getLastName()) ? " " + d.getLastName() : "")) : null;
        return new Summary(service.name(), d.getParticipants(), service.maxParticipants(), d.getDate(),
                d.getDate() == null ? null : d.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                slot == null ? null : slot.start(), slot == null ? null : slot.end(), resources, name, quote,
                zone.getId());
    }

    static CustomerFacts customerFacts(Optional<Customer> linked) {
        return new CustomerFacts(linked.isPresent(), linked.map(Customer::getFirstName).orElse(null));
    }

    private BusinessFacts businessFacts(Business business) {
        String hours = openingHoursService.intervalsOf(business.getId()).stream()
                .sorted(Comparator.comparing(DayInterval::dayOfWeek).thenComparing(DayInterval::startTime))
                .collect(Collectors.groupingBy(DayInterval::day, TreeMap::new, Collectors.mapping(
                        interval -> interval.startTime().format(HH_MM) + "–" + interval.endTime().format(HH_MM),
                        Collectors.joining(", "))))
                .entrySet().stream()
                .map(entry -> entry.getKey().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + entry.getValue())
                .collect(Collectors.joining("; "));
        // Dates that differ from the week, so "are you open on the 24th?" gets the true answer.
        String special = openingHoursService.upcomingSpecialHours(business.getId(),
                        LocalDate.now(business.zoneId()), SPECIAL_HOURS_DAYS).stream()
                .map(day -> day.date().format(SPECIAL_DATE) + (day.note() == null ? "" : " (" + day.note() + ")") + " "
                        + (day.closed() ? "closed" : day.hours().stream()
                        .map(span -> span.startTime().format(HH_MM) + "–" + span.endTime().format(HH_MM))
                        .collect(Collectors.joining(", "))))
                .collect(Collectors.joining("; "));
        if (!special.isEmpty()) {
            hours = (hours.isEmpty() ? "Closed" : hours) + ". Special dates: " + special;
        }
        List<ServiceFact> services = catalog.services(business.getId()).stream()
                .map(s -> new ServiceFact(s.name(), s.category(), s.durationMinutes(), s.price(),
                        s.pricePerParticipant(), s.maxParticipants(), s.currency()))
                .toList();
        List<EventFact> events = eventService.showing(business).stream()
                .map(e -> new EventFact(e.title(), e.performer(), e.description(), e.date(),
                        e.date().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        e.startTime().format(HH_MM), e.endTime() == null ? null : e.endTime().format(HH_MM),
                        e.booking()))
                .toList();
        return new BusinessFacts(business.getName(), business.getPhone(), business.getAddress(),
                catalog.bookingPageUrl(business), hours.isEmpty() ? "Closed" : hours, services, events);
    }
}
