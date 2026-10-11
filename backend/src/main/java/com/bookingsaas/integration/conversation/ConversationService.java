package com.bookingsaas.integration.conversation;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.business.OpeningHoursService;
import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.integration.api.IntegrationCatalogService;
import com.bookingsaas.integration.api.IntegrationDtos.CreateReservationRequest;
import com.bookingsaas.integration.api.IntegrationDtos.CustomerRequest;
import com.bookingsaas.integration.api.IntegrationDtos.Quote;
import com.bookingsaas.integration.api.IntegrationDtos.ReservationInfo;
import com.bookingsaas.integration.api.IntegrationDtos.ResourceChoice;
import com.bookingsaas.integration.api.IntegrationDtos.ResourceOptions;
import com.bookingsaas.integration.api.IntegrationDtos.ServiceInfo;
import com.bookingsaas.integration.api.IntegrationDtos.Slot;
import com.bookingsaas.integration.api.IntegrationReservationViews;
import com.bookingsaas.integration.conversation.ConversationData.Offer;
import com.bookingsaas.integration.conversation.ConversationDtos.ActionFailedRequest;
import com.bookingsaas.integration.conversation.ConversationDtos.Action;
import com.bookingsaas.integration.conversation.ConversationDtos.BusinessFacts;
import com.bookingsaas.integration.conversation.ConversationDtos.Collected;
import com.bookingsaas.integration.conversation.ConversationDtos.ConversationContext;
import com.bookingsaas.integration.conversation.ConversationDtos.CustomerFacts;
import com.bookingsaas.integration.conversation.ConversationDtos.Extraction;
import com.bookingsaas.integration.conversation.ConversationDtos.Option;
import com.bookingsaas.integration.conversation.ConversationDtos.Problem;
import com.bookingsaas.integration.conversation.ConversationDtos.ServiceFact;
import com.bookingsaas.integration.conversation.ConversationDtos.Summary;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnRequest;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnResponse;
import com.bookingsaas.integration.conversation.ConversationEnums.Ask;
import com.bookingsaas.integration.conversation.ConversationEnums.Confirmation;
import com.bookingsaas.integration.conversation.ConversationEnums.Flow;
import com.bookingsaas.integration.conversation.ConversationEnums.Intent;
import com.bookingsaas.integration.conversation.ConversationEnums.NextAction;
import com.bookingsaas.integration.conversation.ConversationEnums.Outcome;
import com.bookingsaas.integration.conversation.ConversationEnums.State;
import com.bookingsaas.integration.conversation.ConversationEnums.TimeOfDay;
import com.bookingsaas.integration.identity.ExternalIdentityService;
import com.bookingsaas.reservation.Reservation;
import com.bookingsaas.reservation.ReservationRepository;
import com.bookingsaas.reservation.ReservationService;
import com.bookingsaas.reservation.ReservationStatus;
import com.bookingsaas.reservation.dto.ResourceSelection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The booking and cancellation conversations, as a deterministic state machine.
 *
 * <p>The language model in n8n interprets each message into an
 * {@link Extraction}; this class merges it into the persisted state, asks the
 * real engines (availability, pricing, cancellation policy) and answers with
 * the facts for the next reply. It never creates or cancels a reservation
 * itself: once the customer explicitly confirms a summary they have seen, it
 * hands the workflow the exact request to send, so the reservation goes
 * through the regular, idempotent integration endpoint.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    /** A chat untouched for this long starts over: yesterday's half-booking is stale. */
    static final Duration CONVERSATION_TTL = Duration.ofHours(24);
    private static final int MAX_TIME_OPTIONS = 10;
    private static final int NEAREST_TIME_OPTIONS = 6;
    private static final int ALTERNATIVE_DATES = 3;
    private static final int ALTERNATIVE_DATE_WINDOW_DAYS = 21;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final ConversationRepository repository;
    private final IntegrationCatalogService catalog;
    private final IntegrationReservationViews views;
    private final BusinessService businessService;
    private final OpeningHoursService openingHoursService;
    private final ExternalIdentityService identities;
    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ConversationService(ConversationRepository repository,
                               IntegrationCatalogService catalog,
                               IntegrationReservationViews views,
                               BusinessService businessService,
                               OpeningHoursService openingHoursService,
                               ExternalIdentityService identities,
                               ReservationRepository reservations,
                               ReservationService reservationService,
                               ObjectMapper objectMapper,
                               Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.views = views;
        this.businessService = businessService;
        this.openingHoursService = openingHoursService;
        this.identities = identities;
        this.reservations = reservations;
        this.reservationService = reservationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Everything that is fixed for the duration of one turn. */
    private record Turn(Business business, ChannelProvider provider, String externalUserId,
                        Optional<Customer> linked, List<Problem> problems) {

        Long businessId() {
            return business.getId();
        }
    }

    // --- context -----------------------------------------------------------

    /** Where the chat stands — what the model needs to interpret the next message. */
    @Transactional
    public ConversationContext context(Long businessId, ChannelProvider provider, String externalUserId) {
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();
        Optional<Customer> linked = identities.linkedCustomer(businessId, provider, externalUserId);
        Optional<Conversation> conversation = active(businessId, provider, externalUserId);
        List<String> serviceNames = catalog.services(businessId).stream().map(ServiceInfo::name).toList();
        LocalDateTime now = LocalDateTime.now(clock.withZone(zone));

        if (conversation.isEmpty()) {
            return new ConversationContext(null, null, null, null, List.of(), null, customerFacts(linked),
                    now.toLocalDate(), now.withNano(0), business.getTimezone(), serviceNames);
        }
        Conversation c = conversation.get();
        ConversationData d = read(c);
        return new ConversationContext(c.getId(), c.getFlow().name(), c.getState().name(), d.getAsked(),
                options(d), new Collected(d.getServiceName(), d.getParticipants(), d.getDate(),
                        d.getTime() == null ? null : d.getTime().format(HH_MM), d.getTimePreference(),
                        d.getFirstName(), d.getPhone(), d.getDiscountCode()),
                customerFacts(linked), now.toLocalDate(), now.withNano(0), business.getTimezone(), serviceNames);
    }

    // --- one message ---------------------------------------------------------

    @Transactional
    public TurnResponse turn(Long businessId, TurnRequest request) {
        Business business = businessService.requireById(businessId);
        Extraction ex = request.extracted() == null ? Extraction.empty() : request.extracted();
        Turn turn = new Turn(business, request.provider(), request.externalUserId(),
                identities.linkedCustomer(businessId, request.provider(), request.externalUserId()),
                new ArrayList<>());
        Conversation conversation = active(businessId, request.provider(), request.externalUserId()).orElse(null);
        Intent intent = ex.intent();

        if (Boolean.TRUE.equals(ex.abort())) {
            if (conversation == null) {
                return idle(turn);
            }
            Outcome outcome = conversation.getFlow() == Flow.BOOKING ? Outcome.BOOKING_ABANDONED
                    : Outcome.CANCELLATION_ABORTED;
            touch(conversation, request);
            conversation.close(State.CANCELLED);
            repository.save(conversation);
            log.info("Conversation {} abandoned by the customer", conversation.getId());
            return ended(turn, conversation, outcome, null);
        }
        if (intent == Intent.RESCHEDULE) {
            turn.problems().add(new Problem("RESCHEDULE_NOT_SUPPORTED",
                    "Changing a booking in chat is not possible yet: the customer can cancel and book again, "
                            + "or contact the business."));
        }

        if (intent == Intent.CANCEL) {
            if (conversation == null || conversation.getFlow() != Flow.CANCELLATION) {
                conversation = start(turn, conversation, Flow.CANCELLATION, request);
            }
            touch(conversation, request);
            return cancellationStep(turn, conversation, ex);
        }
        boolean bookingDetails = ex.service() != null || ex.serviceId() != null || ex.date() != null
                || ex.time() != null || ex.participants() != null;
        if (intent == Intent.BOOK || (conversation == null && bookingDetails)) {
            if (conversation == null || conversation.getFlow() != Flow.BOOKING) {
                conversation = start(turn, conversation, Flow.BOOKING, request);
            }
            touch(conversation, request);
            return bookingStep(turn, conversation, ex);
        }
        if (conversation == null) {
            return idle(turn);
        }
        // An answer to the current question — or a question in between, which
        // is answered from the business facts while the booking stays put.
        touch(conversation, request);
        return conversation.getFlow() == Flow.BOOKING
                ? bookingStep(turn, conversation, ex)
                : cancellationStep(turn, conversation, ex);
    }

    // --- results reported by the workflow ----------------------------------

    /** Called in the reservation's own transaction: the chat ends with the booking. */
    @Transactional
    public void completeBooking(Long businessId, Long conversationId, Reservation reservation) {
        Conversation conversation = require(businessId, conversationId);
        if (conversation.getFlow() != Flow.BOOKING) {
            throw ApiException.validation("Conversation " + conversationId + " is not a booking conversation.");
        }
        conversation.setReservationId(reservation.getId());
        conversation.setCustomerId(reservation.getCustomerId());
        conversation.close(State.COMPLETED);
        repository.save(conversation);
        log.info("Conversation {} completed with reservation {}", conversationId, reservation.getId());
    }

    @Transactional
    public void completeCancellation(Long businessId, Long conversationId, Reservation reservation) {
        Conversation conversation = require(businessId, conversationId);
        if (conversation.getFlow() != Flow.CANCELLATION) {
            throw ApiException.validation("Conversation " + conversationId + " is not a cancellation conversation.");
        }
        conversation.close(State.COMPLETED);
        repository.save(conversation);
        log.info("Conversation {} completed with cancellation of reservation {}", conversationId, reservation.getId());
    }

    /**
     * The reservation (or cancellation) the conversation asked for was
     * refused — typically because someone else took the slot in the meantime.
     * The conversation steps back to the right question with fresh options.
     */
    @Transactional
    public TurnResponse actionFailed(Long businessId, Long conversationId, ActionFailedRequest request) {
        Business business = businessService.requireById(businessId);
        Conversation conversation = require(businessId, conversationId);
        Turn turn = new Turn(business, conversation.getProvider(), conversation.getExternalUserId(),
                identities.linkedCustomer(businessId, conversation.getProvider(), conversation.getExternalUserId()),
                new ArrayList<>());
        conversation.setLastExecutionId(request.executionId());
        ErrorCode code = Arrays.stream(ErrorCode.values())
                .filter(candidate -> candidate.name().equals(request.errorCode()))
                .findFirst().orElse(null);
        log.info("Conversation {} action failed: {} {}", conversationId, request.errorCode(), request.message());

        if (conversation.getFlow() == Flow.CANCELLATION) {
            turn.problems().add(new Problem("CANCELLATION_FAILED", request.message()));
            conversation.close(State.CANCELLED);
            repository.save(conversation);
            return ended(turn, conversation, Outcome.CANCELLATION_FAILED, null);
        }
        if (!conversation.isActive()) {
            throw ApiException.of(ErrorCode.INVALID_RESERVATION_STATE, "This conversation has already ended.");
        }

        ConversationData d = read(conversation);
        LocalTime near = null;
        if (code == null) {
            turn.problems().add(new Problem("TEMPORARY_ERROR",
                    "The booking could not be completed just now; the customer can confirm again in a moment."));
        } else if (code.name().startsWith("DISCOUNT_")) {
            turn.problems().add(new Problem("DISCOUNT_INVALID", request.message()));
            d.setDiscountCode(null);
        } else {
            switch (code) {
                case RESOURCE_NOT_AVAILABLE, OUTSIDE_BUSINESS_HOURS, RESERVATION_TOO_SOON, RESERVATION_IN_THE_PAST,
                     RESERVATION_TOO_FAR_IN_ADVANCE, INVALID_RESOURCE_SELECTION, RESOURCE_SELECTION_NOT_ALLOWED -> {
                    turn.problems().add(new Problem("SLOT_JUST_TAKEN",
                            "That time was booked by someone else in the meantime."));
                    near = d.getTime();
                    d.clearSlot();
                }
                case INVALID_PARTICIPANTS -> {
                    turn.problems().add(new Problem("INVALID_PARTICIPANTS", request.message()));
                    d.setParticipants(null);
                }
                case VALIDATION_FAILED, DUPLICATE_CUSTOMER -> {
                    turn.problems().add(new Problem("INVALID_CUSTOMER_DETAILS", request.message()));
                    d.setPhone(null);
                    d.setEmail(null);
                }
                case SERVICE_INACTIVE, NOT_FOUND -> {
                    turn.problems().add(new Problem("SERVICE_UNAVAILABLE", "That service can no longer be booked."));
                    d.setServiceId(null);
                    d.setServiceName(null);
                    d.clearSlot();
                }
                default -> turn.problems().add(new Problem("TEMPORARY_ERROR",
                        "The booking could not be completed just now; the customer can confirm again in a moment."));
            }
        }
        conversation.setState(State.WAITING_FOR_CONFIRMATION);
        return evaluateBooking(turn, conversation, d, catalog.services(businessId), null, null, true, near);
    }

    // --- booking flow ----------------------------------------------------------

    private TurnResponse bookingStep(Turn turn, Conversation c, Extraction ex) {
        ConversationData d = read(c);
        List<ServiceInfo> services = catalog.services(turn.businessId());
        List<ServiceInfo> candidates = null;
        boolean changed = false;

        if (ex.optionNumber() != null && d.getAsked() != null) {
            Optional<Offer> offer = d.getOffers().stream().filter(o -> o.number() == ex.optionNumber()).findFirst();
            if (offer.isEmpty()) {
                turn.problems().add(new Problem("OPTION_NOT_RECOGNISED",
                        "There is no option " + ex.optionNumber() + "."));
            } else {
                String value = offer.get().value();
                switch (d.getAsked()) {
                    case SERVICE -> changed |= setService(d, find(services, Long.valueOf(value)));
                    case DATE -> changed |= setDate(d, LocalDate.parse(value));
                    case TIME -> changed |= setTime(d, LocalTime.parse(value));
                    case RESOURCE -> {
                        d.getResourceSelections().put(d.getAskedRequirementId(), ids(value));
                        changed = true;
                    }
                    default -> {
                    }
                }
            }
        }

        if (ex.serviceId() != null) {
            ServiceInfo service = find(services, ex.serviceId());
            if (service == null) {
                turn.problems().add(new Problem("SERVICE_NOT_FOUND", "That service is not offered."));
            } else {
                changed |= setService(d, service);
            }
        } else if (hasText(ex.service())) {
            List<ServiceInfo> matches = ServiceMatcher.match(ex.service(), services);
            boolean current = d.getServiceId() != null
                    && matches.stream().anyMatch(match -> match.id().equals(d.getServiceId()));
            if (matches.size() == 1) {
                changed |= setService(d, matches.getFirst());
            } else if (!current) {
                if (matches.isEmpty()) {
                    turn.problems().add(new Problem("SERVICE_NOT_FOUND",
                            "There is no service matching \"" + ex.service().trim() + "\"."));
                } else {
                    candidates = matches;
                    turn.problems().add(new Problem("SERVICE_AMBIGUOUS",
                            "Several services match \"" + ex.service().trim() + "\"."));
                }
                if (d.getServiceId() != null) {
                    d.setServiceId(null);
                    d.setServiceName(null);
                    d.clearSlot();
                    changed = true;
                }
            }
        }
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
        changed |= setText(ex.firstName(), d.getFirstName(), d::setFirstName);
        changed |= setText(ex.lastName(), d.getLastName(), d::setLastName);
        changed |= setText(ex.phone(), d.getPhone(), d::setPhone);
        changed |= setText(ex.email(), d.getEmail(), d::setEmail);
        changed |= setText(ex.discountCode(), d.getDiscountCode(), d::setDiscountCode);

        return evaluateBooking(turn, c, d, services, candidates, ex.confirmation(), changed, null);
    }

    /**
     * Walks the booking from the top and stops at the first thing missing or
     * wrong. Re-checking everything each turn means a changed answer (another
     * day, more people) automatically revalidates what depends on it.
     *
     * @param nearTime when the slot was lost, offer times close to this one
     */
    private TurnResponse evaluateBooking(Turn turn, Conversation c, ConversationData d, List<ServiceInfo> services,
                                         List<ServiceInfo> candidates, Confirmation confirmation, boolean changed,
                                         LocalTime nearTime) {
        State before = c.getState();
        Long businessId = turn.businessId();
        ZoneId zone = turn.business().zoneId();
        List<Problem> problems = turn.problems();

        // 1. service
        ServiceInfo service = d.getServiceId() == null ? null : find(services, d.getServiceId());
        if (d.getServiceId() != null && service == null) {
            problems.add(new Problem("SERVICE_UNAVAILABLE", d.getServiceName() + " can no longer be booked."));
            d.setServiceId(null);
            d.setServiceName(null);
        }
        if (service == null) {
            List<ServiceInfo> offered = candidates != null && !candidates.isEmpty() ? candidates : services;
            List<Offer> offers = numbered(offered.stream()
                    .map(s -> new Offer(0, serviceLabel(s), s.id().toString())).toList());
            return ask(turn, c, d, State.WAITING_FOR_SERVICE, Ask.SERVICE, offers, List.of(), null);
        }

        // 2. group size
        if (service.maxParticipants() == 1) {
            d.setParticipants(1);
        } else if (d.getParticipants() != null && d.getParticipants() > service.maxParticipants()) {
            problems.add(new Problem("INVALID_PARTICIPANTS",
                    service.name() + " is for up to " + service.maxParticipants() + " people."));
            d.setParticipants(null);
        }
        if (d.getParticipants() == null) {
            return ask(turn, c, d, State.WAITING_FOR_PARTICIPANTS, Ask.PARTICIPANTS, List.of(), List.of(),
                    summary(d, service, null, null, zone));
        }

        // 3. date
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (d.getDate() != null && d.getDate().isBefore(today)) {
            problems.add(new Problem("DATE_IN_PAST", d.getDate() + " has already passed."));
            d.setDate(null);
        }
        if (d.getDate() == null) {
            return ask(turn, c, d, State.WAITING_FOR_DATE, Ask.DATE, List.of(), List.of(),
                    summary(d, service, null, null, zone));
        }

        // 4. time — from the availability engine, with the customer rules
        List<Slot> slots = catalog.slots(businessId, service.id(), d.getDate(), d.getParticipants());
        if (slots.isEmpty()) {
            problems.add(new Problem("NO_AVAILABILITY_ON_DATE",
                    "Nothing is free on " + DAY_LABEL.format(d.getDate()) + " for this service and group size."));
            BusinessSettings settings = businessService.settingsOf(businessId);
            LocalDate from = d.getDate().plusDays(1).isBefore(today) ? today : d.getDate().plusDays(1);
            LocalDate to = minDate(from.plusDays(ALTERNATIVE_DATE_WINDOW_DAYS),
                    today.plusDays(settings.getMaximumBookingAdvanceDays()));
            List<Offer> offers = numbered(catalog.availableDates(businessId, service.id(), from, to,
                            d.getParticipants(), ALTERNATIVE_DATES).stream()
                    .map(date -> new Offer(0, DAY_LABEL.format(date), date.toString())).toList());
            d.setDate(null);
            d.clearSlot();
            return ask(turn, c, d, State.WAITING_FOR_DATE, Ask.DATE, offers, List.of(),
                    summary(d, service, null, null, zone));
        }
        Slot chosen = d.getTime() == null ? null : slots.stream()
                .filter(slot -> slot.startTime().toLocalTime().equals(d.getTime()))
                .findFirst().orElse(null);
        if (d.getTime() != null && chosen == null) {
            problems.add(new Problem("TIME_NOT_AVAILABLE",
                    d.getTime().format(HH_MM) + " is not available on " + DAY_LABEL.format(d.getDate()) + "."));
            nearTime = d.getTime();
            d.clearSlot();
        }
        if (chosen == null) {
            List<Slot> pool = slots;
            if (nearTime != null) {
                LocalTime anchor = nearTime;
                pool = slots.stream()
                        .sorted(Comparator.comparingLong(slot -> Math.abs(
                                Duration.between(anchor, slot.startTime().toLocalTime()).toMinutes())))
                        .limit(NEAREST_TIME_OPTIONS)
                        .sorted(Comparator.comparing(Slot::startTime))
                        .toList();
            } else if (d.getTimePreference() != null) {
                List<Slot> preferred = slots.stream()
                        .filter(slot -> within(slot.startTime().toLocalTime(), d.getTimePreference()))
                        .toList();
                if (preferred.isEmpty()) {
                    problems.add(new Problem("NO_TIMES_IN_PREFERENCE", "Nothing is free in the "
                            + d.getTimePreference().name().toLowerCase(Locale.ROOT) + " that day."));
                } else {
                    pool = preferred;
                }
            }
            List<Offer> offers = numbered(spread(pool, MAX_TIME_OPTIONS).stream()
                    .map(slot -> new Offer(0, slot.start() + "–" + slot.end(), slot.start())).toList());
            return ask(turn, c, d, State.WAITING_FOR_TIME, Ask.TIME, offers, List.of(),
                    summary(d, service, null, null, zone));
        }

        // 5. resources the customer may choose
        for (ResourceOptions requirement : chosen.resourceOptions()) {
            List<Long> selected = d.getResourceSelections().get(requirement.requirementId());
            List<Long> stored = selected;
            if (stored != null && !stored.isEmpty() && requirement.choices().stream()
                    .noneMatch(choice -> idsOf(choice).equals(stored))) {
                problems.add(new Problem("RESOURCE_NOT_AVAILABLE",
                        "That " + requirement.resourceTypeName().toLowerCase(Locale.ROOT)
                                + " is not available at this time."));
                d.getResourceSelections().remove(requirement.requirementId());
                selected = null;
            }
            if (selected == null) {
                List<Offer> offers = numbered(requirement.choices().stream()
                        .map(choice -> new Offer(0, choiceLabel(choice), idsOf(choice).stream()
                                .map(String::valueOf).collect(Collectors.joining(","))))
                        .toList());
                d.setAskedRequirementId(requirement.requirementId());
                return ask(turn, c, d, State.WAITING_FOR_RESOURCE, Ask.RESOURCE, offers,
                        List.of(requirement.resourceTypeName()), summary(d, service, chosen, null, zone));
            }
        }

        // 6. who is booking
        if (turn.linked().isEmpty()) {
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
                    problems.add(new Problem("INVALID_PHONE", invalid.getMessage()));
                    d.setPhone(null);
                }
            }
            if (!hasText(d.getPhone()) && !hasText(d.getEmail())) {
                missing.add("phone");
            }
            if (!missing.isEmpty()) {
                return ask(turn, c, d, State.WAITING_FOR_CUSTOMER_INFO, Ask.CUSTOMER_INFO, List.of(), missing,
                        summary(d, service, chosen, null, zone));
            }
        }

        // 7. price, then the explicit confirmation
        Quote quote;
        try {
            quote = catalog.quote(businessId, service.id(), d.getParticipants(), d.getDiscountCode());
        } catch (ApiException ex) {
            if (!ex.code().name().startsWith("DISCOUNT_")) {
                throw ex;
            }
            problems.add(new Problem("DISCOUNT_INVALID", ex.getMessage()));
            d.setDiscountCode(null);
            quote = catalog.quote(businessId, service.id(), d.getParticipants(), null);
        }
        Summary summary = summary(d, service, chosen, quote, zone);
        CreateReservationRequest request = reservationRequest(turn, c, d, service, chosen);

        // Only a "yes" to a summary the customer has already seen, unchanged,
        // creates anything — never a time merely mentioned.
        boolean summaryShown = before == State.WAITING_FOR_CONFIRMATION || before == State.CREATING_RESERVATION;
        if (summaryShown && !changed && problems.isEmpty()) {
            if (confirmation == Confirmation.YES) {
                c.setState(State.CREATING_RESERVATION);
                save(c, d);
                log.info("Conversation {} confirmed: service={} participants={} start={}", c.getId(),
                        service.id(), d.getParticipants(), request.startTime());
                return respond(turn, c, d, NextAction.CREATE_RESERVATION, Ask.NOTHING, List.of(), summary, null,
                        null, new Action(idempotencyKey(c, request), request, null));
            }
            if (confirmation == Confirmation.NO) {
                c.close(State.CANCELLED);
                save(c, d);
                return ended(turn, c, Outcome.BOOKING_ABANDONED, null);
            }
        }
        return ask(turn, c, d, State.WAITING_FOR_CONFIRMATION, Ask.CONFIRMATION, List.of(), List.of(), summary);
    }

    private CreateReservationRequest reservationRequest(Turn turn, Conversation c, ConversationData d,
                                                        ServiceInfo service, Slot slot) {
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

    // --- cancellation flow -----------------------------------------------------

    private TurnResponse cancellationStep(Turn turn, Conversation c, Extraction ex) {
        ConversationData d = read(c);
        State before = c.getState();
        List<Reservation> candidates = cancellable(turn);

        if (before == State.WAITING_FOR_CANCEL_CONFIRMATION || before == State.CANCELLING) {
            Reservation picked = candidates.stream()
                    .filter(reservation -> reservation.getId().equals(d.getCancelReservationId()))
                    .findFirst().orElse(null);
            boolean reselecting = ex.optionNumber() != null || ex.date() != null;
            if (picked != null && !reselecting) {
                if (ex.confirmation() == Confirmation.YES) {
                    c.setState(State.CANCELLING);
                    save(c, d);
                    return respond(turn, c, d, NextAction.CANCEL_RESERVATION, Ask.NOTHING, List.of(), null,
                            views.toInfo(turn.business(), picked), null, new Action(null, null, picked.getId()));
                }
                if (ex.confirmation() == Confirmation.NO) {
                    c.close(State.CANCELLED);
                    save(c, d);
                    return ended(turn, c, Outcome.CANCELLATION_ABORTED, views.toInfo(turn.business(), picked));
                }
                return askCancelConfirmation(turn, c, d, picked);
            }
        }

        if (candidates.isEmpty()) {
            if (turn.linked().isEmpty()) {
                turn.problems().add(new Problem("CUSTOMER_NOT_RECOGNISED",
                        "No reservations are known for this account; bookings made elsewhere can be managed "
                                + "through the link in the confirmation email, or by contacting the business."));
            }
            c.close(State.COMPLETED);
            save(c, d);
            return ended(turn, c, Outcome.NO_UPCOMING_RESERVATIONS, null);
        }

        Reservation picked = null;
        if (ex.optionNumber() != null && d.getAsked() == Ask.CANCEL_SELECTION) {
            picked = d.getOffers().stream()
                    .filter(offer -> offer.number() == ex.optionNumber())
                    .findFirst()
                    .flatMap(offer -> candidates.stream()
                            .filter(reservation -> reservation.getId().toString().equals(offer.value()))
                            .findFirst())
                    .orElse(null);
            if (picked == null) {
                turn.problems().add(new Problem("OPTION_NOT_RECOGNISED", "There is no option " + ex.optionNumber() + "."));
            }
        }
        if (picked == null && ex.date() != null) {
            ZoneId zone = turn.business().zoneId();
            List<Reservation> onDate = candidates.stream()
                    .filter(reservation -> LocalDate.ofInstant(reservation.getStartTime(), zone).equals(ex.date()))
                    .filter(reservation -> ex.time() == null || LocalTime.ofInstant(reservation.getStartTime(), zone)
                            .format(HH_MM).equals(ex.time()))
                    .toList();
            if (onDate.size() == 1) {
                picked = onDate.getFirst();
            }
        }
        if (picked == null && candidates.size() == 1) {
            picked = candidates.getFirst();
        }
        if (picked == null) {
            Map<Long, String> serviceNames = catalog.services(turn.businessId()).stream()
                    .collect(Collectors.toMap(ServiceInfo::id, ServiceInfo::name));
            List<Offer> offers = numbered(candidates.stream()
                    .map(reservation -> new Offer(0, reservationLabel(turn, reservation, serviceNames),
                            reservation.getId().toString()))
                    .toList());
            d.setCancellableIds(candidates.stream().map(Reservation::getId)
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
            List<ReservationInfo> infos = candidates.stream().map(r -> views.toInfo(turn.business(), r)).toList();
            c.setState(State.WAITING_FOR_CANCEL_SELECTION);
            d.setAsked(Ask.CANCEL_SELECTION);
            d.setOffers(offers);
            save(c, d);
            return response(turn, c, d, NextAction.REPLY, Ask.CANCEL_SELECTION, List.of(), null, null, infos, null,
                    null);
        }

        d.setCancelReservationId(picked.getId());
        if (!reservationService.isCancellableByCustomer(turn.business(), picked)) {
            BusinessSettings settings = businessService.settingsOf(turn.businessId());
            turn.problems().add(new Problem("CANCELLATION_NOT_ALLOWED", settings.isAllowCustomerCancellation()
                    ? "Reservations can only be cancelled at least " + settings.getCancellationDeadlineHours()
                    + " hours in advance; the customer should contact the business."
                    : "This business handles cancellations personally; the customer should contact them."));
            c.close(State.COMPLETED);
            save(c, d);
            return ended(turn, c, Outcome.CANCELLATION_NOT_ALLOWED, views.toInfo(turn.business(), picked));
        }
        return askCancelConfirmation(turn, c, d, picked);
    }

    private TurnResponse askCancelConfirmation(Turn turn, Conversation c, ConversationData d, Reservation picked) {
        c.setState(State.WAITING_FOR_CANCEL_CONFIRMATION);
        d.setAsked(Ask.CANCEL_CONFIRMATION);
        d.setOffers(new ArrayList<>());
        save(c, d);
        return respond(turn, c, d, NextAction.REPLY, Ask.CANCEL_CONFIRMATION, List.of(), null,
                views.toInfo(turn.business(), picked), null, null);
    }

    /**
     * What this person may cancel: upcoming reservations of the customer the
     * identity is linked to, plus those booked in this person's own chats.
     */
    private List<Reservation> cancellable(Turn turn) {
        var now = clock.instant();
        List<ReservationStatus> active = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
        Map<Long, Reservation> found = new LinkedHashMap<>();
        turn.linked().ifPresent(customer -> reservations.findUpcomingForCustomer(turn.businessId(), customer.getId(),
                now, active).forEach(reservation -> found.put(reservation.getId(), reservation)));
        List<Long> booked = repository.reservationIdsBookedBy(turn.businessId(), turn.provider(), turn.externalUserId());
        reservations.findAllById(booked).stream()
                .filter(reservation -> reservation.getBusinessId().equals(turn.businessId()))
                .filter(reservation -> reservation.getStatus().isActive() && reservation.getStartTime().isAfter(now))
                .forEach(reservation -> found.putIfAbsent(reservation.getId(), reservation));
        return found.values().stream().sorted(Comparator.comparing(Reservation::getStartTime)).toList();
    }

    // --- responses ---------------------------------------------------------------

    private TurnResponse ask(Turn turn, Conversation c, ConversationData d, State state, Ask ask, List<Offer> offers,
                             List<String> missing, Summary summary) {
        c.setState(state);
        d.setAsked(ask);
        d.setOffers(new ArrayList<>(offers));
        if (ask != Ask.RESOURCE) {
            d.setAskedRequirementId(null);
        }
        save(c, d);
        return respond(turn, c, d, NextAction.REPLY, ask, missing, summary, null, null, null);
    }

    private TurnResponse respond(Turn turn, Conversation c, ConversationData d, NextAction next, Ask ask,
                                 List<String> missing, Summary summary, ReservationInfo reservation, Outcome outcome,
                                 Action action) {
        return response(turn, c, d, next, ask, missing, summary, reservation, null, outcome, action);
    }

    private TurnResponse response(Turn turn, Conversation c, ConversationData d, NextAction next, Ask ask,
                                  List<String> missing, Summary summary, ReservationInfo reservation,
                                  List<ReservationInfo> reservationList, Outcome outcome, Action action) {
        return new TurnResponse(c == null ? null : c.getId(), c == null ? null : c.getFlow().name(),
                c == null ? null : c.getState().name(), next, ask, d == null ? List.of() : options(d),
                List.copyOf(turn.problems()), missing, summary, reservation, reservationList, outcome, action,
                customerFacts(turn.linked()), businessFacts(turn.business()));
    }

    private TurnResponse ended(Turn turn, Conversation c, Outcome outcome, ReservationInfo reservation) {
        return response(turn, c, null, NextAction.REPLY, Ask.NOTHING, List.of(), null, reservation, null, outcome,
                null);
    }

    /** Nothing in progress: a greeting or a question. The reply introduces what the bot can do. */
    private TurnResponse idle(Turn turn) {
        return response(turn, null, null, NextAction.REPLY, Ask.NOTHING, List.of(), null, null, null, null, null);
    }

    private BusinessFacts businessFacts(Business business) {
        String hours = openingHoursService.intervalsOf(business.getId()).stream()
                .sorted(Comparator.comparing(DayInterval::dayOfWeek).thenComparing(DayInterval::startTime))
                .collect(Collectors.groupingBy(DayInterval::day, java.util.TreeMap::new, Collectors.mapping(
                        interval -> interval.startTime().format(HH_MM) + "–" + interval.endTime().format(HH_MM),
                        Collectors.joining(", "))))
                .entrySet().stream()
                .map(entry -> entry.getKey().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + entry.getValue())
                .collect(Collectors.joining("; "));
        List<ServiceFact> services = catalog.services(business.getId()).stream()
                .map(s -> new ServiceFact(s.name(), s.category(), s.durationMinutes(), s.price(),
                        s.pricePerParticipant(), s.maxParticipants(), s.currency()))
                .toList();
        return new BusinessFacts(business.getName(), business.getPhone(), business.getAddress(),
                catalog.bookingPageUrl(business), hours.isEmpty() ? "Closed" : hours, services);
    }

    private static CustomerFacts customerFacts(Optional<Customer> linked) {
        return new CustomerFacts(linked.isPresent(), linked.map(Customer::getFirstName).orElse(null));
    }

    private static List<Option> options(ConversationData d) {
        return d.getOffers().stream().map(offer -> new Option(offer.number(), offer.label(), offer.value())).toList();
    }

    private Summary summary(ConversationData d, ServiceInfo service, Slot slot, Quote quote, ZoneId zone) {
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

    // --- state helpers ------------------------------------------------------------

    private Optional<Conversation> active(Long businessId, ChannelProvider provider, String externalUserId) {
        Optional<Conversation> found = repository.findByBusinessIdAndProviderAndExternalUserIdAndActiveTrue(
                businessId, provider, externalUserId);
        if (found.isPresent() && found.get().getUpdatedAt() != null
                && found.get().getUpdatedAt().plus(CONVERSATION_TTL).isBefore(clock.instant())) {
            Conversation stale = found.get();
            stale.close(State.CANCELLED);
            repository.saveAndFlush(stale);
            log.info("Conversation {} expired", stale.getId());
            return Optional.empty();
        }
        return found;
    }

    private Conversation start(Turn turn, Conversation previous, Flow flow, TurnRequest request) {
        if (previous != null) {
            previous.close(State.CANCELLED);
            repository.saveAndFlush(previous);
        }
        Conversation conversation = new Conversation();
        conversation.setBusinessId(turn.businessId());
        conversation.setProvider(turn.provider());
        conversation.setExternalUserId(turn.externalUserId());
        conversation.setCustomerId(turn.linked().map(Customer::getId).orElse(null));
        conversation.setFlow(flow);
        conversation.setState(flow == Flow.BOOKING ? State.WAITING_FOR_SERVICE : State.WAITING_FOR_CANCEL_SELECTION);
        ConversationData data = new ConversationData();
        data.setDisplayName(request.displayName());
        conversation.setData(write(data));
        Conversation saved = repository.saveAndFlush(conversation);
        log.info("Conversation {} started: flow={} provider={} externalUser={}", saved.getId(), flow,
                turn.provider(), turn.externalUserId());
        return saved;
    }

    private static void touch(Conversation conversation, TurnRequest request) {
        if (request.message() != null) {
            conversation.setLastMessage(request.message().length() > 2000
                    ? request.message().substring(0, 2000) : request.message());
        }
        if (request.executionId() != null) {
            conversation.setLastExecutionId(request.executionId());
        }
    }

    private Conversation require(Long businessId, Long conversationId) {
        return repository.findByIdAndBusinessId(conversationId, businessId)
                .orElseThrow(() -> ApiException.notFound("Conversation", conversationId));
    }

    private ConversationData read(Conversation conversation) {
        try {
            return objectMapper.readValue(conversation.getData(), ConversationData.class);
        } catch (JsonProcessingException ex) {
            log.warn("Unreadable state on conversation {}; starting it over", conversation.getId());
            return new ConversationData();
        }
    }

    private String write(ConversationData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not store conversation state", ex);
        }
    }

    private void save(Conversation c, ConversationData d) {
        c.setData(write(d));
        repository.save(c);
    }

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

    private static boolean setText(String value, String current, java.util.function.Consumer<String> setter) {
        if (!hasText(value) || value.trim().equals(current)) {
            return false;
        }
        setter.accept(value.trim());
        return true;
    }

    // --- small utilities -----------------------------------------------------------

    private static ServiceInfo find(List<ServiceInfo> services, Long id) {
        return services.stream().filter(service -> service.id().equals(id)).findFirst().orElse(null);
    }

    private static List<Offer> numbered(List<Offer> offers) {
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

    private static String serviceLabel(ServiceInfo service) {
        String price = service.price() == null ? "" : " · " + service.price().stripTrailingZeros().toPlainString()
                + " " + service.currency() + (service.pricePerParticipant() ? " per person" : "");
        return service.name() + " · " + service.durationMinutes() + " min" + price;
    }

    private static String choiceLabel(ResourceChoice choice) {
        String names = choice.resources().stream().map(r -> r.name()).collect(Collectors.joining(" + "));
        return choice.area() == null ? names : names + " (" + choice.area() + ")";
    }

    private static List<Long> idsOf(ResourceChoice choice) {
        return choice.resources().stream().map(r -> r.id()).toList();
    }

    private static List<Long> ids(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }

    private static String reservationLabel(Turn turn, Reservation reservation, Map<Long, String> serviceNames) {
        LocalDateTime start = LocalDateTime.ofInstant(reservation.getStartTime(), turn.business().zoneId());
        return serviceNames.getOrDefault(reservation.getServiceId(), "Reservation") + " · "
                + DAY_LABEL.format(start) + " " + start.toLocalTime().format(HH_MM);
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
