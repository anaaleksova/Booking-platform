package com.bookingsaas.integration.conversation;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.integration.api.IntegrationCatalogService;
import com.bookingsaas.integration.api.IntegrationDtos.ServiceInfo;
import com.bookingsaas.integration.conversation.ConversationDtos.ActionFailedRequest;
import com.bookingsaas.integration.conversation.ConversationDtos.Collected;
import com.bookingsaas.integration.conversation.ConversationDtos.ConversationContext;
import com.bookingsaas.integration.conversation.ConversationDtos.Extraction;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnRequest;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnResponse;
import com.bookingsaas.integration.conversation.ConversationEnums.Flow;
import com.bookingsaas.integration.conversation.ConversationEnums.Intent;
import com.bookingsaas.integration.conversation.ConversationEnums.Outcome;
import com.bookingsaas.integration.conversation.ConversationEnums.State;
import com.bookingsaas.integration.identity.ExternalIdentityService;
import com.bookingsaas.reservation.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.bookingsaas.integration.conversation.ConversationText.HH_MM;
import static com.bookingsaas.integration.conversation.ConversationText.options;
import static com.bookingsaas.integration.conversation.ConversationText.resourceNames;

/**
 * The booking and cancellation conversations, as a deterministic state machine.
 *
 * <p>The language model in n8n interprets each message into an
 * {@link Extraction}; the flows ({@link BookingFlow}, {@link CancellationFlow})
 * merge it into the persisted state, ask the real engines (availability,
 * pricing, cancellation policy) and answer with the facts for the next reply.
 * Nothing here creates or cancels a reservation itself: once the customer
 * explicitly confirms a summary they have seen, the workflow is handed the
 * exact request to send, so the reservation goes through the regular,
 * idempotent integration endpoint.
 *
 * <p>This class only decides which flow a message belongs to, and takes the
 * results the workflow reports back.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationStore store;
    private final ConversationReplies replies;
    private final BookingFlow booking;
    private final CancellationFlow cancellation;
    private final IntegrationCatalogService catalog;
    private final BusinessService businessService;
    private final ExternalIdentityService identities;
    private final Clock clock;

    ConversationService(ConversationStore store,
                        ConversationReplies replies,
                        BookingFlow booking,
                        CancellationFlow cancellation,
                        IntegrationCatalogService catalog,
                        BusinessService businessService,
                        ExternalIdentityService identities,
                        Clock clock) {
        this.store = store;
        this.replies = replies;
        this.booking = booking;
        this.cancellation = cancellation;
        this.catalog = catalog;
        this.businessService = businessService;
        this.identities = identities;
        this.clock = clock;
    }

    // --- context -----------------------------------------------------------

    /** Where the chat stands — what the model needs to interpret the next message. */
    @Transactional
    public ConversationContext context(Long businessId, ChannelProvider provider, String externalUserId) {
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();
        Optional<Customer> linked = identities.linkedCustomer(businessId, provider, externalUserId);
        Optional<Conversation> conversation = store.active(businessId, provider, externalUserId);
        List<ServiceInfo> services = catalog.services(businessId);
        List<String> serviceNames = services.stream().map(ServiceInfo::name).toList();
        LocalDateTime now = LocalDateTime.now(clock.withZone(zone));

        if (conversation.isEmpty()) {
            return new ConversationContext(null, null, null, null, List.of(), null,
                    ConversationReplies.customerFacts(linked), now.toLocalDate(), now.withNano(0),
                    business.getTimezone(), serviceNames, resourceNames(services, null));
        }
        Conversation c = conversation.get();
        ConversationData d = store.read(c);
        return new ConversationContext(c.getId(), c.getFlow().name(), c.getState().name(), d.getAsked(),
                options(d), new Collected(d.getServiceName(), d.getParticipants(), d.getDate(),
                        d.getTime() == null ? null : d.getTime().format(HH_MM), d.getTimePreference(),
                        d.getFirstName(), d.getPhone(), d.getDiscountCode()),
                ConversationReplies.customerFacts(linked), now.toLocalDate(), now.withNano(0),
                business.getTimezone(), serviceNames, resourceNames(services, d.getServiceId()));
    }

    // --- one message ---------------------------------------------------------

    @Transactional
    public TurnResponse turn(Long businessId, TurnRequest request) {
        Business business = businessService.requireById(businessId);
        Extraction ex = request.extracted() == null ? Extraction.empty() : request.extracted();
        // One message at a time per person: a later one waits and sees what this one changed.
        store.lockPerson(businessId, request.provider(), request.externalUserId());
        ConversationTurn turn = new ConversationTurn(business, request.provider(), request.externalUserId(),
                identities.linkedCustomer(businessId, request.provider(), request.externalUserId()));
        Conversation conversation = store.active(businessId, request.provider(), request.externalUserId())
                .orElse(null);
        if (conversation != null && ConversationStore.isRepeat(conversation, request)) {
            log.info("Conversation {}: repeated message ignored", conversation.getId());
            return ConversationDtos.nothingToSay(conversation);
        }
        Intent intent = ex.intent();

        if (Boolean.TRUE.equals(ex.abort())) {
            if (conversation == null) {
                return replies.idle(turn);
            }
            Outcome outcome = conversation.getFlow() == Flow.BOOKING ? Outcome.BOOKING_ABANDONED
                    : Outcome.CANCELLATION_ABORTED;
            ConversationStore.touch(conversation, request);
            conversation.close(State.CANCELLED);
            store.save(conversation);
            log.info("Conversation {} abandoned by the customer", conversation.getId());
            return replies.ended(turn, conversation, outcome, null);
        }
        if (intent == Intent.RESCHEDULE) {
            turn.problem("RESCHEDULE_NOT_SUPPORTED",
                    "Changing a booking in chat is not possible yet: the customer can cancel and book again, "
                            + "or contact the business.");
        }

        if (intent == Intent.CANCEL) {
            if (conversation == null || conversation.getFlow() != Flow.CANCELLATION) {
                conversation = store.start(turn, conversation, Flow.CANCELLATION, request);
            }
            ConversationStore.touch(conversation, request);
            return cancellation.step(turn, conversation, ex);
        }
        boolean bookingDetails = ex.service() != null || ex.serviceId() != null || ex.date() != null
                || ex.time() != null || ex.participants() != null;
        if (intent == Intent.BOOK || (conversation == null && bookingDetails)) {
            if (conversation == null || conversation.getFlow() != Flow.BOOKING) {
                conversation = store.start(turn, conversation, Flow.BOOKING, request);
            }
            ConversationStore.touch(conversation, request);
            return booking.step(turn, conversation, ex);
        }
        if (conversation == null) {
            return replies.idle(turn);
        }
        // An answer to the current question — or a question in between, which
        // is answered from the business facts while the booking stays put.
        ConversationStore.touch(conversation, request);
        return conversation.getFlow() == Flow.BOOKING
                ? booking.step(turn, conversation, ex)
                : cancellation.step(turn, conversation, ex);
    }

    /** The chat channel a conversation takes place in. */
    @Transactional(readOnly = true)
    public ChannelProvider providerOf(Long businessId, Long conversationId) {
        return store.require(businessId, conversationId).getProvider();
    }

    // --- results reported by the workflow ----------------------------------

    /** Called in the reservation's own transaction: the chat ends with the booking. */
    @Transactional
    public void completeBooking(Long businessId, Long conversationId, Reservation reservation) {
        Conversation conversation = store.requireLocked(businessId, conversationId);
        if (conversation.getFlow() != Flow.BOOKING) {
            throw ApiException.validation("Conversation " + conversationId + " is not a booking conversation.");
        }
        conversation.setReservationId(reservation.getId());
        conversation.setCustomerId(reservation.getCustomerId());
        conversation.close(State.COMPLETED);
        store.save(conversation);
        log.info("Conversation {} completed with reservation {}", conversationId, reservation.getId());
    }

    @Transactional
    public void completeCancellation(Long businessId, Long conversationId, Reservation reservation) {
        Conversation conversation = store.requireLocked(businessId, conversationId);
        if (conversation.getFlow() != Flow.CANCELLATION) {
            throw ApiException.validation("Conversation " + conversationId + " is not a cancellation conversation.");
        }
        conversation.close(State.COMPLETED);
        store.save(conversation);
        log.info("Conversation {} completed with cancellation of reservation {}", conversationId, reservation.getId());
    }

    /**
     * The reservation (or cancellation) the conversation asked for was
     * refused — typically because someone else took the slot in the meantime.
     * A booking steps back to the right question with fresh options; a
     * cancellation ends, with the reason.
     */
    @Transactional
    public TurnResponse actionFailed(Long businessId, Long conversationId, ActionFailedRequest request) {
        Business business = businessService.requireById(businessId);
        Conversation conversation = store.requireLocked(businessId, conversationId);
        ConversationTurn turn = new ConversationTurn(business, conversation.getProvider(),
                conversation.getExternalUserId(), identities.linkedCustomer(businessId, conversation.getProvider(),
                conversation.getExternalUserId()));
        conversation.setLastExecutionId(request.executionId());
        ErrorCode code = Arrays.stream(ErrorCode.values())
                .filter(candidate -> candidate.name().equals(request.errorCode()))
                .findFirst().orElse(null);
        log.info("Conversation {} action failed: {} {}", conversationId, request.errorCode(), request.message());

        if (conversation.getFlow() == Flow.CANCELLATION) {
            turn.problem("CANCELLATION_FAILED", request.message());
            conversation.close(State.CANCELLED);
            store.save(conversation);
            return replies.ended(turn, conversation, Outcome.CANCELLATION_FAILED, null);
        }
        if (!conversation.isActive()) {
            throw ApiException.of(ErrorCode.INVALID_RESERVATION_STATE, "This conversation has already ended.");
        }
        return booking.recover(turn, conversation, request, code);
    }
}
