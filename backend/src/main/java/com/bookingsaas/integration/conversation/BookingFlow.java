package com.bookingsaas.integration.conversation;

import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.integration.api.IntegrationCatalogService;
import com.bookingsaas.integration.api.IntegrationDtos.CreateReservationRequest;
import com.bookingsaas.integration.api.IntegrationDtos.CustomerRequest;
import com.bookingsaas.integration.api.IntegrationDtos.Quote;
import com.bookingsaas.integration.api.IntegrationDtos.ResourceChoice;
import com.bookingsaas.integration.api.IntegrationDtos.ResourceOptions;
import com.bookingsaas.integration.api.IntegrationDtos.ServiceInfo;
import com.bookingsaas.integration.api.IntegrationDtos.Slot;
import com.bookingsaas.integration.conversation.ConversationData.Offer;
import com.bookingsaas.integration.conversation.ConversationDtos.Action;
import com.bookingsaas.integration.conversation.ConversationDtos.ActionFailedRequest;
import com.bookingsaas.integration.conversation.ConversationDtos.Extraction;
import com.bookingsaas.integration.conversation.ConversationDtos.Summary;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnResponse;
import com.bookingsaas.integration.conversation.ConversationEnums.Ask;
import com.bookingsaas.integration.conversation.ConversationEnums.Confirmation;
import com.bookingsaas.integration.conversation.ConversationEnums.NextAction;
import com.bookingsaas.integration.conversation.ConversationEnums.Outcome;
import com.bookingsaas.integration.conversation.ConversationEnums.State;
import com.bookingsaas.integration.identity.ExternalIdentityService;
import com.bookingsaas.reservation.dto.ResourceSelection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.bookingsaas.integration.conversation.ConversationText.DAY_LABEL;
import static com.bookingsaas.integration.conversation.ConversationText.HH_MM;
import static com.bookingsaas.integration.conversation.ConversationText.choiceLabel;
import static com.bookingsaas.integration.conversation.ConversationText.find;
import static com.bookingsaas.integration.conversation.ConversationText.hasText;
import static com.bookingsaas.integration.conversation.ConversationText.ids;
import static com.bookingsaas.integration.conversation.ConversationText.idsOf;
import static com.bookingsaas.integration.conversation.ConversationText.matchChoice;
import static com.bookingsaas.integration.conversation.ConversationText.mentions;
import static com.bookingsaas.integration.conversation.ConversationText.minDate;
import static com.bookingsaas.integration.conversation.ConversationText.numbered;
import static com.bookingsaas.integration.conversation.ConversationText.requirementResourceNames;
import static com.bookingsaas.integration.conversation.ConversationText.resourceNames;
import static com.bookingsaas.integration.conversation.ConversationText.serviceLabel;
import static com.bookingsaas.integration.conversation.ConversationText.spread;
import static com.bookingsaas.integration.conversation.ConversationText.within;

/**
 * The booking conversation: service → group size → date → time → resources
 * the customer may choose → who is booking → price and an explicit "yes".
 *
 * <p>Every turn merges what the customer said into the stored state, then
 * walks these steps from the top and asks about the first one missing or
 * wrong. Re-checking everything each turn means a changed answer (another
 * day, more people) automatically revalidates what depends on it.
 */
@Component
class BookingFlow {

    private static final Logger log = LoggerFactory.getLogger(BookingFlow.class);
    private static final int MAX_TIME_OPTIONS = 10;
    private static final int NEAREST_TIME_OPTIONS = 6;
    private static final int ALTERNATIVE_DATES = 3;
    private static final int ALTERNATIVE_DATE_WINDOW_DAYS = 21;

    private final ConversationStore store;
    private final ConversationReplies replies;
    private final IntegrationCatalogService catalog;
    private final BusinessService businessService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    BookingFlow(ConversationStore store, ConversationReplies replies, IntegrationCatalogService catalog,
                BusinessService businessService, ObjectMapper objectMapper, Clock clock) {
        this.store = store;
        this.replies = replies;
        this.catalog = catalog;
        this.businessService = businessService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** One message in a booking conversation. */
    TurnResponse step(ConversationTurn turn, Conversation c, Extraction ex) {
        ConversationData d = store.read(c);
        List<ServiceInfo> services = catalog.services(turn.businessId());
        boolean changed = applyChosenOption(turn, d, ex, services);
        Merge service = mergeService(turn, d, ex, services);
        changed |= service.changed();
        changed |= mergeDetails(d, ex);
        return evaluate(turn, c, d, services, service.candidates(), ex.confirmation(), changed, null);
    }

    /**
     * The reservation this conversation asked for was refused, typically
     * because someone else took the slot in the meantime: forget what no
     * longer holds and step back to the right question with fresh options.
     */
    TurnResponse recover(ConversationTurn turn, Conversation c, ActionFailedRequest request, ErrorCode code) {
        ConversationData d = store.read(c);
        LocalTime near = null;
        if (code == null) {
            turn.problem("TEMPORARY_ERROR",
                    "The booking could not be completed just now; the customer can confirm again in a moment.");
        } else if (code.name().startsWith("DISCOUNT_")) {
            turn.problem("DISCOUNT_INVALID", request.message());
            d.setDiscountCode(null);
        } else {
            switch (code) {
                case RESOURCE_NOT_AVAILABLE, OUTSIDE_BUSINESS_HOURS, RESERVATION_TOO_SOON, RESERVATION_IN_THE_PAST,
                     RESERVATION_TOO_FAR_IN_ADVANCE, INVALID_RESOURCE_SELECTION, RESOURCE_SELECTION_NOT_ALLOWED -> {
                    turn.problem("SLOT_JUST_TAKEN", "That time was booked by someone else in the meantime.");
                    near = d.getTime();
                    d.clearSlot();
                }
                case INVALID_PARTICIPANTS -> {
                    turn.problem("INVALID_PARTICIPANTS", request.message());
                    d.setParticipants(null);
                }
                case VALIDATION_FAILED, DUPLICATE_CUSTOMER -> {
                    turn.problem("INVALID_CUSTOMER_DETAILS", request.message());
                    d.setPhone(null);
                    d.setEmail(null);
                }
                case SERVICE_INACTIVE, NOT_FOUND -> {
                    turn.problem("SERVICE_UNAVAILABLE", "That service can no longer be booked.");
                    d.setServiceId(null);
                    d.setServiceName(null);
                    d.clearSlot();
                }
                default -> turn.problem("TEMPORARY_ERROR",
                        "The booking could not be completed just now; the customer can confirm again in a moment.");
            }
        }
        c.setState(State.WAITING_FOR_CONFIRMATION);
        return evaluate(turn, c, d, catalog.services(turn.businessId()), null, null, true, near);
    }

    // --- merging what the customer said -----------------------------------------

    /** A service and the alternatives to offer when the name was ambiguous. */
    private record Merge(boolean changed, List<ServiceInfo> candidates) {
    }

    /** "2": an answer to the numbered question that was asked. */
    private static boolean applyChosenOption(ConversationTurn turn, ConversationData d, Extraction ex,
                                             List<ServiceInfo> services) {
        if (ex.optionNumber() == null || d.getAsked() == null) {
            return false;
        }
        Optional<Offer> offer = d.getOffers().stream().filter(o -> o.number() == ex.optionNumber()).findFirst();
        if (offer.isEmpty()) {
            turn.problem("OPTION_NOT_RECOGNISED", "There is no option " + ex.optionNumber() + ".");
            return false;
        }
        String value = offer.get().value();
        return switch (d.getAsked()) {
            case SERVICE -> setService(d, find(services, Long.valueOf(value)));
            case DATE -> setDate(d, LocalDate.parse(value));
            case TIME -> setTime(d, LocalTime.parse(value));
            case RESOURCE -> {
                d.getResourceSelections().put(d.getAskedRequirementId(), ids(value));
                // Remembered by name, so a later change of time keeps the same choice.
                d.setPreferredResource(offer.get().label());
                d.setAnyResource(false);
                yield true;
            }
            default -> false;
        };
    }

    private static Merge mergeService(ConversationTurn turn, ConversationData d, Extraction ex,
                                      List<ServiceInfo> services) {
        if (ex.serviceId() != null) {
            ServiceInfo service = find(services, ex.serviceId());
            if (service == null) {
                turn.problem("SERVICE_NOT_FOUND", "That service is not offered.");
                return new Merge(false, null);
            }
            return new Merge(setService(d, service), null);
        }
        if (!hasText(ex.service())) {
            return new Merge(false, null);
        }
        List<ServiceInfo> matches = ServiceMatcher.match(ex.service(), services);
        boolean current = d.getServiceId() != null
                && matches.stream().anyMatch(match -> match.id().equals(d.getServiceId()));
        if (matches.size() == 1) {
            return new Merge(setService(d, matches.getFirst()), null);
        }
        if (current) {
            return new Merge(false, null);
        }
        List<ServiceInfo> candidates = null;
        if (matches.isEmpty()) {
            turn.problem("SERVICE_NOT_FOUND", "There is no service matching \"" + ex.service().trim() + "\".");
        } else {
            candidates = matches;
            turn.problem("SERVICE_AMBIGUOUS", "Several services match \"" + ex.service().trim() + "\".");
        }
        boolean changed = false;
        if (d.getServiceId() != null) {
            d.setServiceId(null);
            d.setServiceName(null);
            d.clearSlot();
            changed = true;
        }
        return new Merge(changed, candidates);
    }

    /** Group size, date, time, preferences and contact details. */
    private static boolean mergeDetails(ConversationData d, Extraction ex) {
        boolean changed = false;
        if (ex.participants() != null && !ex.participants().equals(d.getParticipants())) {
            d.setParticipants(ex.participants());
            d.clearSlot();
            changed = true;
        }
        if (ex.date() != null) {
            changed |= setDate(d, ex.date());
        }
        if (ex.time() != null) {
            changed |= setTime(d, LocalTime.parse(ex.time().length() == 4 ? "0" + ex.time() : ex.time()));
        }
        if (ex.timePreference() != null && ex.timePreference() != d.getTimePreference()) {
            d.setTimePreference(ex.timePreference());
            if (ex.time() == null && d.getTime() != null && !within(d.getTime(), ex.timePreference())) {
                d.clearSlot();
            }
            changed = true;
        }
        if (Boolean.TRUE.equals(ex.noPreference()) && d.getAsked() == Ask.RESOURCE
                && d.getAskedRequirementId() != null) {
            d.getResourceSelections().put(d.getAskedRequirementId(), List.of());
            changed = true;
        }
        // Who or what was asked for by name before the question came up ("14:00 with Elena"),
        // or "anyone is fine" said up front: both spare the customer the resource question.
        if (hasText(ex.resource()) && !ex.resource().trim().equalsIgnoreCase(
                Objects.toString(d.getPreferredResource(), ""))) {
            d.setPreferredResource(ex.resource().trim());
            d.setAnyResource(false);
            d.setResourceSelections(new LinkedHashMap<>());
            changed = true;
        } else if (Boolean.TRUE.equals(ex.noPreference()) && d.getAsked() != Ask.RESOURCE && !d.isAnyResource()) {
            d.setAnyResource(true);
            d.setPreferredResource(null);
            changed = true;
        }
        changed |= setText(ex.firstName(), d.getFirstName(), d::setFirstName);
        changed |= setText(ex.lastName(), d.getLastName(), d::setLastName);
        changed |= setText(ex.phone(), d.getPhone(), d::setPhone);
        changed |= setText(ex.email(), d.getEmail(), d::setEmail);
        changed |= setText(ex.discountCode(), d.getDiscountCode(), d::setDiscountCode);
        return changed;
    }

    // --- walking the booking from the top -----------------------------------------

    /**
     * Stops at the first thing missing or wrong and asks about it.
     *
     * @param nearTime when the slot was lost, offer times close to this one
     */
    private TurnResponse evaluate(ConversationTurn turn, Conversation c, ConversationData d,
                                  List<ServiceInfo> services, List<ServiceInfo> candidates,
                                  Confirmation confirmation, boolean changed, LocalTime nearTime) {
        State before = c.getState();
        ZoneId zone = turn.business().zoneId();

        ServiceInfo service = currentService(turn, d, services);
        if (service == null) {
            List<ServiceInfo> offered = candidates != null && !candidates.isEmpty() ? candidates : services;
            List<Offer> offers = numbered(offered.stream()
                    .map(s -> new Offer(0, serviceLabel(s), s.id().toString())).toList());
            return replies.ask(turn, c, d, State.WAITING_FOR_SERVICE, Ask.SERVICE, offers, List.of(), null);
        }

        checkParticipants(turn, d, service);
        if (d.getParticipants() == null) {
            return replies.ask(turn, c, d, State.WAITING_FOR_PARTICIPANTS, Ask.PARTICIPANTS, List.of(), List.of(),
                    replies.summary(d, service, null, null, zone));
        }

        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (d.getDate() != null && d.getDate().isBefore(today)) {
            turn.problem("DATE_IN_PAST", d.getDate() + " has already passed.");
            d.setDate(null);
        }
        if (d.getDate() == null) {
            return replies.ask(turn, c, d, State.WAITING_FOR_DATE, Ask.DATE, List.of(), List.of(),
                    replies.summary(d, service, null, null, zone));
        }

        // Times come from the availability engine, with the customer rules.
        List<Slot> slots = catalog.slots(turn.businessId(), service.id(), d.getDate(), d.getParticipants());
        if (slots.isEmpty()) {
            return offerOtherDates(turn, c, d, service, today);
        }
        Slot chosen = d.getTime() == null ? null : slots.stream()
                .filter(slot -> slot.startTime().toLocalTime().equals(d.getTime()))
                .findFirst().orElse(null);
        if (d.getTime() != null && chosen == null) {
            turn.problem("TIME_NOT_AVAILABLE",
                    d.getTime().format(HH_MM) + " is not available on " + DAY_LABEL.format(d.getDate()) + ".");
            nearTime = d.getTime();
            d.clearSlot();
        }
        if (chosen == null) {
            return askTime(turn, c, d, services, service, slots, nearTime);
        }

        TurnResponse resourceQuestion = askResources(turn, c, d, service, chosen);
        if (resourceQuestion != null) {
            return resourceQuestion;
        }
        TurnResponse detailsQuestion = askCustomerDetails(turn, c, d, service, chosen);
        if (detailsQuestion != null) {
            return detailsQuestion;
        }
        return confirm(turn, c, d, service, chosen, before, confirmation, changed);
    }

    private static ServiceInfo currentService(ConversationTurn turn, ConversationData d, List<ServiceInfo> services) {
        ServiceInfo service = d.getServiceId() == null ? null : find(services, d.getServiceId());
        if (d.getServiceId() != null && service == null) {
            turn.problem("SERVICE_UNAVAILABLE", d.getServiceName() + " can no longer be booked.");
            d.setServiceId(null);
            d.setServiceName(null);
        }
        return service;
    }

    private static void checkParticipants(ConversationTurn turn, ConversationData d, ServiceInfo service) {
        if (service.maxParticipants() == 1) {
            d.setParticipants(1);
        } else if (d.getParticipants() != null && d.getParticipants() > service.maxParticipants()) {
            turn.problem("INVALID_PARTICIPANTS",
                    service.name() + " is for up to " + service.maxParticipants() + " people.");
            d.setParticipants(null);
        }
    }

    /** Nothing is free on the chosen day: the next few days that have something. */
    private TurnResponse offerOtherDates(ConversationTurn turn, Conversation c, ConversationData d,
                                         ServiceInfo service, LocalDate today) {
        turn.problem("NO_AVAILABILITY_ON_DATE",
                "Nothing is free on " + DAY_LABEL.format(d.getDate()) + " for this service and group size.");
        BusinessSettings settings = businessService.settingsOf(turn.businessId());
        LocalDate from = d.getDate().plusDays(1).isBefore(today) ? today : d.getDate().plusDays(1);
        LocalDate to = minDate(from.plusDays(ALTERNATIVE_DATE_WINDOW_DAYS),
                today.plusDays(settings.getMaximumBookingAdvanceDays()));
        List<Offer> offers = numbered(catalog.availableDates(turn.businessId(), service.id(), from, to,
                        d.getParticipants(), ALTERNATIVE_DATES).stream()
                .map(date -> new Offer(0, DAY_LABEL.format(date), date.toString())).toList());
        d.setDate(null);
        d.clearSlot();
        return replies.ask(turn, c, d, State.WAITING_FOR_DATE, Ask.DATE, offers, List.of(),
                replies.summary(d, service, null, null, turn.business().zoneId()));
    }

    /**
     * The times to offer: near a lost slot, within the part of the day asked
     * for, and only when a resource asked for by name is free.
     */
    private TurnResponse askTime(ConversationTurn turn, Conversation c, ConversationData d,
                                 List<ServiceInfo> services, ServiceInfo service, List<Slot> slots,
                                 LocalTime nearTime) {
        List<Slot> pool = slots;
        if (nearTime != null) {
            pool = slots.stream()
                    .sorted(Comparator.comparingLong(slot -> Math.abs(
                            Duration.between(nearTime, slot.startTime().toLocalTime()).toMinutes())))
                    .limit(NEAREST_TIME_OPTIONS)
                    .sorted(Comparator.comparing(Slot::startTime))
                    .toList();
        } else if (d.getTimePreference() != null) {
            List<Slot> preferred = slots.stream()
                    .filter(slot -> within(slot.startTime().toLocalTime(), d.getTimePreference()))
                    .toList();
            if (preferred.isEmpty()) {
                turn.problem("NO_TIMES_IN_PREFERENCE", "Nothing is free in the "
                        + d.getTimePreference().name().toLowerCase(Locale.ROOT) + " that day.");
            } else {
                pool = preferred;
            }
        }
        // Asked for someone by name: offer only the times they are free.
        if (hasText(d.getPreferredResource()) && mentions(resourceNames(services, service.id()),
                d.getPreferredResource())) {
            List<Slot> withPreferred = pool.stream()
                    .filter(slot -> slot.resourceOptions().stream()
                            .anyMatch(options -> matchChoice(options.choices(), d.getPreferredResource()) != null))
                    .toList();
            if (withPreferred.isEmpty()) {
                turn.problem("RESOURCE_NOT_AVAILABLE", d.getPreferredResource() + " has no free times that day.");
            } else {
                pool = withPreferred;
            }
        }
        List<Offer> offers = numbered(spread(pool, MAX_TIME_OPTIONS).stream()
                .map(slot -> new Offer(0, slot.start() + "–" + slot.end(), slot.start())).toList());
        return replies.ask(turn, c, d, State.WAITING_FOR_TIME, Ask.TIME, offers, List.of(),
                replies.summary(d, service, null, null, turn.business().zoneId()));
    }

    /** Settles each resource the customer may choose, or asks about the first one still open. */
    private TurnResponse askResources(ConversationTurn turn, Conversation c, ConversationData d,
                                      ServiceInfo service, Slot chosen) {
        for (ResourceOptions requirement : chosen.resourceOptions()) {
            List<Long> selected = d.getResourceSelections().get(requirement.requirementId());
            List<Long> stored = selected;
            if (stored != null && !stored.isEmpty() && requirement.choices().stream()
                    .noneMatch(choice -> idsOf(choice).equals(stored))) {
                turn.problem("RESOURCE_NOT_AVAILABLE", "That " + requirement.resourceTypeName()
                        .toLowerCase(Locale.ROOT) + " is not available at this time.");
                d.getResourceSelections().remove(requirement.requirementId());
                selected = null;
            }
            boolean preferredTaken = false;
            if (selected == null && hasText(d.getPreferredResource())) {
                ResourceChoice match = matchChoice(requirement.choices(), d.getPreferredResource());
                if (match != null) {
                    selected = idsOf(match);
                    d.getResourceSelections().put(requirement.requirementId(), selected);
                } else if (mentions(requirementResourceNames(service, requirement.requirementId()),
                        d.getPreferredResource())) {
                    // Named, but not free at this time: say so and let them pick.
                    turn.problem("RESOURCE_NOT_AVAILABLE", d.getPreferredResource() + " is not available at this time.");
                    preferredTaken = true;
                }
            }
            if (selected == null && d.isAnyResource() && !preferredTaken) {
                selected = List.of();
                d.getResourceSelections().put(requirement.requirementId(), selected);
            }
            if (selected == null) {
                List<Offer> offers = numbered(requirement.choices().stream()
                        .map(choice -> new Offer(0, choiceLabel(choice), idsOf(choice).stream()
                                .map(String::valueOf).collect(Collectors.joining(","))))
                        .toList());
                d.setAskedRequirementId(requirement.requirementId());
                return replies.ask(turn, c, d, State.WAITING_FOR_RESOURCE, Ask.RESOURCE, offers,
                        List.of(requirement.resourceTypeName()),
                        replies.summary(d, service, chosen, null, turn.business().zoneId()));
            }
        }
        return null;
    }

    /** A name and a phone (or email), unless the chat is linked to a known customer. */
    private TurnResponse askCustomerDetails(ConversationTurn turn, Conversation c, ConversationData d,
                                            ServiceInfo service, Slot chosen) {
        if (turn.linked().isPresent()) {
            return null;
        }
        List<String> missing = new ArrayList<>();
        if (!hasText(d.getFirstName())) {
            missing.add("firstName");
        }
        if (hasText(d.getPhone())) {
            try {
                // Stored in international form, so the reservation request carries it unambiguously.
                d.setPhone(ExternalIdentityService.normalizePhone(d.getPhone(),
                        ExternalIdentityService.regionOf(turn.business().getPhone())));
            } catch (ApiException invalid) {
                // The number is quoted back, so a typo is easy to spot.
                turn.problem("INVALID_PHONE", d.getPhone().trim() + ": " + invalid.getMessage());
                d.setPhone(null);
            }
        }
        if (!hasText(d.getPhone()) && !hasText(d.getEmail())) {
            missing.add("phone");
        }
        if (missing.isEmpty()) {
            return null;
        }
        return replies.ask(turn, c, d, State.WAITING_FOR_CUSTOMER_INFO, Ask.CUSTOMER_INFO, List.of(), missing,
                replies.summary(d, service, chosen, null, turn.business().zoneId()));
    }

    /** The price, then the explicit confirmation. */
    private TurnResponse confirm(ConversationTurn turn, Conversation c, ConversationData d, ServiceInfo service,
                                 Slot chosen, State before, Confirmation confirmation, boolean changed) {
        Quote quote;
        try {
            quote = catalog.quote(turn.businessId(), service.id(), d.getParticipants(), d.getDiscountCode());
        } catch (ApiException ex) {
            if (!ex.code().name().startsWith("DISCOUNT_")) {
                throw ex;
            }
            turn.problem("DISCOUNT_INVALID", ex.getMessage());
            d.setDiscountCode(null);
            quote = catalog.quote(turn.businessId(), service.id(), d.getParticipants(), null);
        }
        Summary summary = replies.summary(d, service, chosen, quote, turn.business().zoneId());
        CreateReservationRequest request = reservationRequest(turn, c, d, service, chosen);

        // Only a "yes" to a summary the customer has already seen, unchanged,
        // creates anything — never a time merely mentioned.
        boolean summaryShown = before == State.WAITING_FOR_CONFIRMATION || before == State.CREATING_RESERVATION;
        if (summaryShown && !changed && turn.problems().isEmpty()) {
            if (confirmation == Confirmation.YES) {
                c.setState(State.CREATING_RESERVATION);
                store.save(c, d);
                log.info("Conversation {} confirmed: service={} participants={} start={}", c.getId(),
                        service.id(), d.getParticipants(), request.startTime());
                return replies.respond(turn, c, d, NextAction.CREATE_RESERVATION, Ask.NOTHING, List.of(), summary,
                        null, null, new Action(idempotencyKey(c, request), request, null));
            }
            if (confirmation == Confirmation.NO) {
                c.close(State.CANCELLED);
                store.save(c, d);
                return replies.ended(turn, c, Outcome.BOOKING_ABANDONED, null);
            }
        }
        return replies.ask(turn, c, d, State.WAITING_FOR_CONFIRMATION, Ask.CONFIRMATION, List.of(), List.of(),
                summary);
    }

    private static CreateReservationRequest reservationRequest(ConversationTurn turn, Conversation c,
                                                               ConversationData d, ServiceInfo service, Slot slot) {
        List<ResourceSelection> selections = d.getResourceSelections().entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> new ResourceSelection(entry.getKey(), entry.getValue()))
                .toList();
        CustomerRequest customer = turn.linked().isPresent() ? null
                : new CustomerRequest(turn.provider(), turn.externalUserId(), d.getDisplayName(), d.getFirstName(),
                        d.getLastName(), d.getPhone(), d.getEmail());
        return new CreateReservationRequest(service.id(), d.getParticipants(), slot.startTime(),
                selections.isEmpty() ? null : selections, d.getDiscountCode(),
                turn.linked().map(Customer::getId).orElse(null), customer, null, c.getId());
    }

    /** Same conversation and same details → same key, so a repeated "yes" cannot book twice. */
    private String idempotencyKey(Conversation c, CreateReservationRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return "conv-" + c.getId() + "-" + HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("Could not derive an idempotency key", ex);
        }
    }

    // --- state setters: each says whether anything changed --------------------------

    private static boolean setService(ConversationData d, ServiceInfo service) {
        if (service == null || service.id().equals(d.getServiceId())) {
            return false;
        }
        d.setServiceId(service.id());
        d.setServiceName(service.name());
        d.clearSlot();
        return true;
    }

    private static boolean setDate(ConversationData d, LocalDate date) {
        if (date.equals(d.getDate())) {
            return false;
        }
        d.setDate(date);
        d.clearSlot();
        return true;
    }

    private static boolean setTime(ConversationData d, LocalTime time) {
        if (time.equals(d.getTime())) {
            return false;
        }
        d.setTime(time);
        d.setResourceSelections(new LinkedHashMap<>());
        return true;
    }

    private static boolean setText(String value, String current, Consumer<String> setter) {
        if (!hasText(value) || value.trim().equals(current)) {
            return false;
        }
        setter.accept(value.trim());
        return true;
    }
}
