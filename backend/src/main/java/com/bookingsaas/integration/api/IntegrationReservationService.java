package com.bookingsaas.integration.api;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerService;
import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.integration.api.IntegrationDtos.CancelReservationRequest;
import com.bookingsaas.integration.api.IntegrationDtos.CreateReservationRequest;
import com.bookingsaas.integration.api.IntegrationDtos.CustomerInfo;
import com.bookingsaas.integration.api.IntegrationDtos.CustomerRequest;
import com.bookingsaas.integration.api.IntegrationDtos.ReservationInfo;
import com.bookingsaas.integration.conversation.ConversationService;
import com.bookingsaas.integration.identity.ExternalIdentityService;
import com.bookingsaas.integration.identity.ExternalIdentityService.ContactDetails;
import com.bookingsaas.integration.identity.ExternalIdentityService.ResolvedCustomer;
import com.bookingsaas.reservation.IntegrationReservationCommand;
import com.bookingsaas.reservation.Reservation;
import com.bookingsaas.reservation.ReservationRepository;
import com.bookingsaas.reservation.ReservationService;
import com.bookingsaas.reservation.ReservationSource;
import com.bookingsaas.reservation.ReservationStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Writes through the integration API: customers, reservations, cancellations.
 * Every write is delegated to the same services the website uses; this class
 * adds only what automation needs on top — idempotency, social identities and
 * closing the conversation that asked for it.
 */
@Service
public class IntegrationReservationService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationReservationService.class);
    static final String IDEMPOTENCY_CONSTRAINT = "uq_integration_idempotency";
    /** Recorded when no reason is given; the notification workflow recognises it. */
    public static final String CHAT_CANCELLATION_REASON = "Cancelled by the customer via chat";

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final BusinessService businessService;
    private final CustomerService customerService;
    private final ExternalIdentityService identities;
    private final ConversationService conversations;
    private final IntegrationIdempotencyRepository idempotency;
    private final IntegrationReservationViews views;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IntegrationReservationService(ReservationService reservationService,
                                         ReservationRepository reservationRepository,
                                         BusinessService businessService,
                                         CustomerService customerService,
                                         ExternalIdentityService identities,
                                         ConversationService conversations,
                                         IntegrationIdempotencyRepository idempotency,
                                         IntegrationReservationViews views,
                                         ObjectMapper objectMapper,
                                         Clock clock) {
        this.reservationService = reservationService;
        this.reservationRepository = reservationRepository;
        this.businessService = businessService;
        this.customerService = customerService;
        this.identities = identities;
        this.conversations = conversations;
        this.idempotency = idempotency;
        this.views = views;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** @param replayed true when an earlier request with the same Idempotency-Key is being answered again */
    public record CreateResult(ReservationInfo reservation, boolean replayed) {
    }

    // --- customers ---------------------------------------------------------

    @Transactional
    public CustomerInfo findOrCreateCustomer(Long businessId, CustomerRequest request) {
        ResolvedCustomer resolved = identities.resolve(businessId, request.provider(), request.externalUserId(),
                request.displayName(), new ContactDetails(request.firstName(), request.lastName(), request.phone(),
                        request.email()));
        return toInfo(resolved.customer(), resolved.linked(), resolved.created());
    }

    @Transactional(readOnly = true)
    public Optional<CustomerInfo> findCustomer(Long businessId, ChannelProvider provider, String externalUserId,
                                               String phone, String email) {
        if (provider != null && externalUserId != null) {
            return identities.linkedCustomer(businessId, provider, externalUserId)
                    .map(customer -> toInfo(customer, true, false));
        }
        if (phone == null && email == null) {
            throw ApiException.validation("Give provider and externalUserId, or a phone number or email.");
        }
        String normalizedPhone = phone == null ? null : ExternalIdentityService.normalizePhone(phone);
        return customerService.findByContact(businessId, email, normalizedPhone)
                .map(customer -> toInfo(customer, false, false));
    }

    // --- reservations --------------------------------------------------------

    /**
     * Creates a reservation exactly as the website would, once per
     * Idempotency-Key. The key is claimed before anything else is written, so
     * a simultaneous retry blocks on it and then gets the original answer
     * (via {@link #replay}) instead of a second booking or a misleading
     * "slot taken".
     */
    @Transactional
    public CreateResult create(Long businessId, String idempotencyKey, CreateReservationRequest request) {
        String requestHash = hash(request);
        if (idempotencyKey != null) {
            Optional<IntegrationIdempotency> existing = idempotency.findByBusinessIdAndOperationAndIdempotencyKey(
                    businessId, IntegrationIdempotency.CREATE_RESERVATION, idempotencyKey);
            if (existing.isPresent()) {
                return replayOf(businessId, existing.get(), requestHash);
            }
        }
        IntegrationIdempotency claim = null;
        if (idempotencyKey != null) {
            claim = new IntegrationIdempotency();
            claim.setBusinessId(businessId);
            claim.setOperation(IntegrationIdempotency.CREATE_RESERVATION);
            claim.setIdempotencyKey(idempotencyKey);
            claim.setRequestHash(requestHash);
            claim = idempotency.saveAndFlush(claim);
        }

        Customer customer = resolveCustomer(businessId, request);
        ReservationSource source = request.externalCustomer() != null
                ? request.externalCustomer().provider().reservationSource()
                : ReservationSource.API;
        Reservation reservation = reservationService.createFromIntegration(businessId, customer,
                new IntegrationReservationCommand(request.serviceId(), request.numberOfParticipants(),
                        request.startTime(), request.resourceSelections(), request.discountCode(), request.notes(),
                        source));

        if (claim != null) {
            claim.setReservationId(reservation.getId());
            idempotency.save(claim);
        }
        if (request.conversationId() != null) {
            conversations.completeBooking(businessId, request.conversationId(), reservation);
        }
        log.info("Integration reservation {} created for customer {} (conversation={}, key={})", reservation.getId(),
                customer.getId(), request.conversationId(), idempotencyKey);
        return new CreateResult(views.toInfo(businessService.requireById(businessId), reservation), false);
    }

    /** The answer to a request whose key was claimed by a simultaneous one that has since committed. */
    @Transactional(readOnly = true)
    public CreateResult replay(Long businessId, String idempotencyKey, CreateReservationRequest request) {
        IntegrationIdempotency existing = idempotency.findByBusinessIdAndOperationAndIdempotencyKey(
                        businessId, IntegrationIdempotency.CREATE_RESERVATION, idempotencyKey)
                .orElseThrow(() -> ApiException.of(ErrorCode.CONFLICT,
                        "A request with this Idempotency-Key is still in progress. Retry shortly."));
        return replayOf(businessId, existing, hash(request));
    }

    private CreateResult replayOf(Long businessId, IntegrationIdempotency existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw ApiException.of(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used for a different reservation request.");
        }
        Reservation reservation = reservationRepository.findByIdAndBusinessId(existing.getReservationId(), businessId)
                .orElseThrow(() -> ApiException.notFound("Reservation", existing.getReservationId()));
        log.info("Idempotent replay of reservation {} for key {}", reservation.getId(), existing.getIdempotencyKey());
        return new CreateResult(views.toInfo(businessService.requireById(businessId), reservation), true);
    }

    @Transactional(readOnly = true)
    public ReservationInfo get(Long businessId, Long reservationId) {
        Business business = businessService.requireById(businessId);
        Reservation reservation = reservationRepository.findByIdAndBusinessId(reservationId, businessId)
                .orElseThrow(() -> ApiException.notFound("Reservation", reservationId));
        return views.toInfo(business, reservation);
    }

    /** A customer's upcoming, still active reservations, soonest first. */
    @Transactional(readOnly = true)
    public List<ReservationInfo> upcomingFor(Long businessId, Long customerId) {
        Business business = businessService.requireById(businessId);
        customerService.requireForBusiness(businessId, customerId);
        return reservationRepository.findUpcomingForCustomer(businessId, customerId, clock.instant(),
                        List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)).stream()
                .map(reservation -> views.toInfo(business, reservation))
                .toList();
    }

    @Transactional
    public ReservationInfo cancel(Long businessId, Long reservationId, CancelReservationRequest request) {
        String reason = request == null ? null : request.reason();
        Reservation reservation = reservationService.cancelFromIntegration(businessId, reservationId,
                reason == null || reason.isBlank() ? CHAT_CANCELLATION_REASON : reason);
        if (request != null && request.conversationId() != null) {
            conversations.completeCancellation(businessId, request.conversationId(), reservation);
        }
        return views.toInfo(businessService.requireById(businessId), reservation);
    }

    // --- helpers ---------------------------------------------------------------

    private Customer resolveCustomer(Long businessId, CreateReservationRequest request) {
        if (request.customerId() != null) {
            return customerService.requireForBusiness(businessId, request.customerId());
        }
        if (request.externalCustomer() == null) {
            throw ApiException.validation("Give either customerId or externalCustomer.");
        }
        CustomerRequest external = request.externalCustomer();
        return identities.resolve(businessId, external.provider(), external.externalUserId(), external.displayName(),
                new ContactDetails(external.firstName(), external.lastName(), external.phone(), external.email()))
                .customer();
    }

    private static CustomerInfo toInfo(Customer customer, boolean linked, boolean created) {
        return new CustomerInfo(customer.getId(), customer.getFirstName(), customer.getLastName(), customer.getPhone(),
                customer.getEmail(), linked, created);
    }

    private String hash(CreateReservationRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("Could not fingerprint the request", ex);
        }
    }

    /** Whether a failed write lost the race for an Idempotency-Key. */
    static boolean isIdempotencyRace(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }
}
