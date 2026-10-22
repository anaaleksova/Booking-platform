package com.bookingsaas.integration.conversation;

import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.integration.api.IntegrationCatalogService;
import com.bookingsaas.integration.api.IntegrationDtos.ReservationInfo;
import com.bookingsaas.integration.api.IntegrationDtos.ServiceInfo;
import com.bookingsaas.integration.api.IntegrationReservationViews;
import com.bookingsaas.integration.conversation.ConversationData.Offer;
import com.bookingsaas.integration.conversation.ConversationDtos.Action;
import com.bookingsaas.integration.conversation.ConversationDtos.Extraction;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnResponse;
import com.bookingsaas.integration.conversation.ConversationEnums.Ask;
import com.bookingsaas.integration.conversation.ConversationEnums.Confirmation;
import com.bookingsaas.integration.conversation.ConversationEnums.NextAction;
import com.bookingsaas.integration.conversation.ConversationEnums.Outcome;
import com.bookingsaas.integration.conversation.ConversationEnums.State;
import com.bookingsaas.reservation.Reservation;
import com.bookingsaas.reservation.ReservationRepository;
import com.bookingsaas.reservation.ReservationService;
import com.bookingsaas.reservation.ReservationStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.bookingsaas.integration.conversation.ConversationText.HH_MM;
import static com.bookingsaas.integration.conversation.ConversationText.numbered;
import static com.bookingsaas.integration.conversation.ConversationText.reservationLabel;

/**
 * The cancellation conversation: which upcoming reservation, then an
 * explicit "yes". The business's cancellation policy applies exactly as on
 * the website, and only reservations this person may manage are offered.
 */
@Component
class CancellationFlow {

    private final ConversationStore store;
    private final ConversationReplies replies;
    private final IntegrationCatalogService catalog;
    private final IntegrationReservationViews views;
    private final BusinessService businessService;
    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final ConversationRepository repository;
    private final Clock clock;

    CancellationFlow(ConversationStore store, ConversationReplies replies, IntegrationCatalogService catalog,
                     IntegrationReservationViews views, BusinessService businessService,
                     ReservationRepository reservations, ReservationService reservationService,
                     ConversationRepository repository, Clock clock) {
        this.store = store;
        this.replies = replies;
        this.catalog = catalog;
        this.views = views;
        this.businessService = businessService;
        this.reservations = reservations;
        this.reservationService = reservationService;
        this.repository = repository;
        this.clock = clock;
    }

    /** One message in a cancellation conversation. */
    TurnResponse step(ConversationTurn turn, Conversation c, Extraction ex) {
        ConversationData d = store.read(c);
        State before = c.getState();
        List<Reservation> candidates = cancellable(turn);

        if (before == State.WAITING_FOR_CANCEL_CONFIRMATION || before == State.CANCELLING) {
            TurnResponse answered = answerConfirmation(turn, c, d, ex, candidates);
            if (answered != null) {
                return answered;
            }
        }

        if (candidates.isEmpty()) {
            if (turn.linked().isEmpty()) {
                turn.problem("CUSTOMER_NOT_RECOGNISED",
                        "No reservations are known for this account; bookings made elsewhere can be managed "
                                + "through the link in the confirmation email, or by contacting the business.");
            }
            c.close(State.COMPLETED);
            store.save(c, d);
            return replies.ended(turn, c, Outcome.NO_UPCOMING_RESERVATIONS, null);
        }

        Reservation picked = pick(turn, d, ex, candidates);
        if (picked == null) {
            return askWhich(turn, c, d, candidates);
        }

        d.setCancelReservationId(picked.getId());
        if (!reservationService.isCancellableByCustomer(turn.business(), picked)) {
            BusinessSettings settings = businessService.settingsOf(turn.businessId());
            turn.problem("CANCELLATION_NOT_ALLOWED", settings.isAllowCustomerCancellation()
                    ? "Reservations can only be cancelled at least " + settings.getCancellationDeadlineHours()
                    + " hours in advance; the customer should contact the business."
                    : "This business handles cancellations personally; the customer should contact them.");
            c.close(State.COMPLETED);
            store.save(c, d);
            return replies.ended(turn, c, Outcome.CANCELLATION_NOT_ALLOWED, views.toInfo(turn.business(), picked));
        }
        return askConfirmation(turn, c, d, picked);
    }

    /** A yes or no to the reservation already put to them; null when they are choosing again instead. */
    private TurnResponse answerConfirmation(ConversationTurn turn, Conversation c, ConversationData d, Extraction ex,
                                            List<Reservation> candidates) {
        Reservation picked = candidates.stream()
                .filter(reservation -> reservation.getId().equals(d.getCancelReservationId()))
                .findFirst().orElse(null);
        boolean reselecting = ex.optionNumber() != null || ex.date() != null;
        if (picked == null || reselecting) {
            return null;
        }
        if (ex.confirmation() == Confirmation.YES) {
            c.setState(State.CANCELLING);
            store.save(c, d);
            return replies.respond(turn, c, d, NextAction.CANCEL_RESERVATION, Ask.NOTHING, List.of(), null,
                    views.toInfo(turn.business(), picked), null, new Action(null, null, picked.getId()));
        }
        if (ex.confirmation() == Confirmation.NO) {
            c.close(State.CANCELLED);
            store.save(c, d);
            return replies.ended(turn, c, Outcome.CANCELLATION_ABORTED, views.toInfo(turn.business(), picked));
        }
        return askConfirmation(turn, c, d, picked);
    }

    /** By option number, by date (and time), or the only one there is. */
    private static Reservation pick(ConversationTurn turn, ConversationData d, Extraction ex,
                                    List<Reservation> candidates) {
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
                turn.problem("OPTION_NOT_RECOGNISED", "There is no option " + ex.optionNumber() + ".");
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
        return picked;
    }

    private TurnResponse askWhich(ConversationTurn turn, Conversation c, ConversationData d,
                                  List<Reservation> candidates) {
        Map<Long, String> serviceNames = catalog.services(turn.businessId()).stream()
                .collect(Collectors.toMap(ServiceInfo::id, ServiceInfo::name));
        ZoneId zone = turn.business().zoneId();
        List<Offer> offers = numbered(candidates.stream()
                .map(reservation -> new Offer(0, reservationLabel(reservation, zone, serviceNames),
                        reservation.getId().toString()))
                .toList());
        d.setCancellableIds(candidates.stream().map(Reservation::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<ReservationInfo> infos = candidates.stream().map(r -> views.toInfo(turn.business(), r)).toList();
        c.setState(State.WAITING_FOR_CANCEL_SELECTION);
        d.setAsked(Ask.CANCEL_SELECTION);
        d.setOffers(offers);
        store.save(c, d);
        return replies.response(turn, c, d, NextAction.REPLY, Ask.CANCEL_SELECTION, List.of(), null, null, infos,
                null, null);
    }

    private TurnResponse askConfirmation(ConversationTurn turn, Conversation c, ConversationData d,
                                         Reservation picked) {
        c.setState(State.WAITING_FOR_CANCEL_CONFIRMATION);
        d.setAsked(Ask.CANCEL_CONFIRMATION);
        d.setOffers(new ArrayList<>());
        store.save(c, d);
        return replies.respond(turn, c, d, NextAction.REPLY, Ask.CANCEL_CONFIRMATION, List.of(), null,
                views.toInfo(turn.business(), picked), null, null);
    }

    /**
     * What this person may cancel: upcoming reservations of the customer the
     * identity is linked to, plus those booked in this person's own chats.
     */
    private List<Reservation> cancellable(ConversationTurn turn) {
        Instant now = clock.instant();
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
}
