package com.bookingsaas.reservation;

import com.bookingsaas.availability.AvailabilityService;
import com.bookingsaas.availability.ResourceAllocation;
import com.bookingsaas.availability.dto.SlotResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerService;
import com.bookingsaas.discount.DiscountService;
import com.bookingsaas.notification.ReservationNotificationEvent;
import com.bookingsaas.notification.ReservationNotificationPayload;
import com.bookingsaas.pricing.PriceQuote;
import com.bookingsaas.pricing.PricingService;
import com.bookingsaas.reservation.dto.AdminReservationRequest;
import com.bookingsaas.reservation.dto.PublicReservationRequest;
import com.bookingsaas.reservation.dto.PublicReservationResponse;
import com.bookingsaas.reservation.dto.RescheduleRequest;
import com.bookingsaas.reservation.dto.ReservationResponse;
import com.bookingsaas.reservation.dto.ResourceSelection;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The reservation lifecycle, and the only place reservations are written.
 *
 * <p>Double booking is prevented in three layers, deliberately overlapping:
 *
 * <ol>
 *   <li>the availability engine never offers a taken slot;</li>
 *   <li>{@link AvailabilityService#allocate} re-checks the exact time and the
 *       chosen resources <em>inside</em> this transaction, so a slot taken
 *       since the customer loaded the page is caught;</li>
 *   <li>a PostgreSQL exclusion constraint on {@code reservation_resource}
 *       rejects the second of two genuinely simultaneous inserts.</li>
 * </ol>
 *
 * <p>Layer 3 is the one that holds under concurrency — layers 1 and 2 exist
 * to turn a race into a clear 409 rather than a database error.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final String OVERLAP_CONSTRAINT = "excl_reservation_resource_overlap";
    /** Raised by the trigger that guards the seats of shared resources. */
    private static final String SEATS_GUARD = "reservation_resource_seats";

    private final ReservationRepository repository;
    private final ReservationViewAssembler assembler;
    private final ReservationAccess access;
    private final AvailabilityService availabilityService;
    private final PricingService pricingService;
    private final DiscountService discountService;
    private final BusinessService businessService;
    private final ServiceCatalogService serviceCatalog;
    private final CustomerService customerService;
    private final ApplicationEventPublisher events;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public ReservationService(ReservationRepository repository,
                              ReservationViewAssembler assembler,
                              ReservationAccess access,
                              AvailabilityService availabilityService,
                              PricingService pricingService,
                              DiscountService discountService,
                              BusinessService businessService,
                              ServiceCatalogService serviceCatalog,
                              CustomerService customerService,
                              ApplicationEventPublisher events,
                              CurrentTenant currentTenant,
                              Clock clock) {
        this.repository = repository;
        this.assembler = assembler;
        this.access = access;
        this.availabilityService = availabilityService;
        this.pricingService = pricingService;
        this.discountService = discountService;
        this.businessService = businessService;
        this.serviceCatalog = serviceCatalog;
        this.customerService = customerService;
        this.events = events;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    // --- creation ----------------------------------------------------------

    /** A reservation made by a customer. Every rule applies, including lead times. */
    @Transactional
    public PublicReservationResponse createFromCustomer(String businessSlug, PublicReservationRequest request) {
        Business business = businessService.requireActiveBySlug(businessSlug);
        Long businessId = business.getId();
        BookableService service = serviceCatalog.requireBookable(businessId, request.serviceId());
        int participants = request.participants();

        // Price first: an invalid discount code should fail before anything is written.
        PriceQuote quote = pricingService.quote(business, service, participants, request.discountCode());
        ResourceAllocation allocation = availabilityService.allocate(businessId, service,
                toInstant(request.startTime(), business.zoneId()), participants,
                selections(request.resourceSelections()), null, true);

        // The account's email, never the one in the request: it is what ties
        // the booking to the person who is signed in.
        String email = currentTenant.user().email();
        Customer customer = customerService.findOrCreate(businessId, request.customer().firstName(),
                request.customer().lastName(), email, request.customer().phone());

        Reservation reservation = build(business, service, customer, participants, allocation, quote,
                ReservationSource.PUBLIC, request.notes(), null, null);
        Reservation saved = persist(reservation, quote);

        log.info("Reservation created id={} business={} service={} participants={} resources={} source=PUBLIC",
                saved.getId(), businessId, service.getId(), participants, saved.resourceIds());
        events.publishEvent(ReservationNotificationEvent.created(payloadFor(business, saved, null)));
        return assembler.toPublic(business, saved, isCancellableByCustomer(business, saved));
    }

    /** A reservation taken by the business. Lead-time rules do not apply. */
    @Transactional
    public ReservationResponse createFromAdmin(AdminReservationRequest request) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        BookableService service = serviceCatalog.requireBookable(businessId, request.serviceId());
        int participants = request.participants();

        PriceQuote quote = pricingService.quote(business, service, participants, request.discountCode());
        ResourceAllocation allocation = availabilityService.allocate(businessId, service,
                toInstant(request.startTime(), business.zoneId()), participants,
                selections(request.resourceSelections()), null, false);
        Customer customer = resolveCustomer(businessId, request);

        Reservation reservation = build(business, service, customer, participants, allocation, quote,
                ReservationSource.ADMIN, request.customerNotes(), request.internalNotes(), currentTenant.userId());
        Reservation saved = persist(reservation, quote);

        log.info("Reservation created id={} business={} service={} participants={} resources={} source=ADMIN",
                saved.getId(), businessId, service.getId(), participants, saved.resourceIds());
        events.publishEvent(ReservationNotificationEvent.created(payloadFor(business, saved, null)));
        return assembler.toResponse(business, saved);
    }

    private Reservation build(Business business,
                              BookableService service,
                              Customer customer,
                              int participants,
                              ResourceAllocation allocation,
                              PriceQuote quote,
                              ReservationSource source,
                              String customerNotes,
                              String internalNotes,
                              Long actorUserId) {
        Reservation reservation = new Reservation();
        reservation.setBusinessId(business.getId());
        reservation.setServiceId(service.getId());
        reservation.setCustomerId(customer.getId());
        reservation.setNumberOfParticipants(participants);
        // The MVP confirms immediately; PENDING exists for a later approval flow.
        reservation.changeStatus(ReservationStatus.CONFIRMED);
        reservation.schedule(allocation);
        reservation.assignResources(allocation.resources());
        reservation.setOriginalPrice(quote.originalPrice());
        reservation.setDiscountAmount(quote.discountAmount());
        reservation.setFinalPrice(quote.finalPrice());
        reservation.setCurrency(quote.currency());
        reservation.setDiscountId(quote.discountId());
        reservation.setDiscountCode(quote.discountCode());
        reservation.setSource(source);
        reservation.setCustomerNotes(blankToNull(customerNotes));
        reservation.setInternalNotes(blankToNull(internalNotes));
        reservation.setCreatedByUserId(actorUserId);
        return reservation;
    }

    /**
     * Takes the discount use and writes the reservation. An exclusion-constraint
     * violation — the branch two simultaneous customers actually hit — becomes
     * the same 409 the pre-check would have produced.
     */
    private Reservation persist(Reservation reservation, PriceQuote quote) {
        if (quote.hasDiscount()) {
            discountService.redeem(quote.discountId());
        }
        return saveAndFlush(reservation);
    }

    private Reservation saveAndFlush(Reservation reservation) {
        try {
            return repository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            if (mentions(ex, OVERLAP_CONSTRAINT) || mentions(ex, SEATS_GUARD)) {
                log.info("Concurrent reservation rejected by exclusion constraint for business {}",
                        reservation.getBusinessId());
                throw ApiException.of(ErrorCode.RESOURCE_NOT_AVAILABLE,
                        "The selected resources are no longer available.");
            }
            throw ex;
        }
    }

    private Customer resolveCustomer(Long businessId, AdminReservationRequest request) {
        if (request.customerId() != null) {
            return customerService.requireForBusiness(businessId, request.customerId());
        }
        if (request.customer() == null) {
            throw ApiException.validation("Select an existing customer or enter new customer details.");
        }
        AdminReservationRequest.NewCustomer details = request.customer();
        return customerService.findOrCreate(businessId, details.firstName(), details.lastName(),
                details.email(), details.phone());
    }

    // --- lifecycle ---------------------------------------------------------

    /** Confirm, complete or cancel, following {@link ReservationStatus#allowedTransitions()}. */
    @Transactional
    public ReservationResponse changeStatus(Long id, ReservationStatus target, String reason) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        Reservation reservation = requireReservation(businessId, id);
        access.requireAccess(businessId, reservation);

        if (reservation.getStatus() == target) {
            return assembler.toResponse(business, reservation);
        }
        if (!reservation.getStatus().allowedTransitions().contains(target)) {
            throw ApiException.of(ErrorCode.INVALID_RESERVATION_STATE, "A " + describe(reservation.getStatus())
                    + " reservation cannot be marked as " + describe(target) + ".");
        }

        if (target == ReservationStatus.CANCELLED) {
            // The business may always cancel — the deadline protects the
            // business from late customer cancellations, not the reverse.
            applyCancellation(reservation, reason);
            log.info("Reservation cancelled id={} business={} by user={}", id, businessId, currentTenant.userId());
            events.publishEvent(ReservationNotificationEvent.cancelled(payloadFor(business, reservation, reason)));
        } else {
            reservation.changeStatus(target);
            repository.save(reservation);
        }
        return assembler.toResponse(business, reservation);
    }

    /**
     * Moves a reservation and/or changes its resources. Without an explicit
     * selection the current resources are kept when they are still free at the
     * new time; otherwise the best available ones are assigned.
     */
    @Transactional
    public ReservationResponse reschedule(Long id, RescheduleRequest request) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        Reservation reservation = requireReservation(businessId, id);
        access.requireAccess(businessId, reservation);
        if (!reservation.getStatus().isActive()) {
            throw ApiException.of(ErrorCode.INVALID_RESERVATION_STATE,
                    "Only a pending or confirmed reservation can be changed.");
        }

        BookableService service = serviceCatalog.requireForBusiness(businessId, reservation.getServiceId());
        Instant newStart = toInstant(request.startTime(), business.zoneId());
        LocalDateTime previousLocal = LocalDateTime.ofInstant(reservation.getStartTime(), business.zoneId());
        boolean moved = !newStart.equals(reservation.getStartTime());

        ResourceAllocation allocation = request.resourceSelections() != null
                ? availabilityService.allocate(businessId, service, newStart, reservation.getNumberOfParticipants(),
                        selections(request.resourceSelections()), reservation.getId(), false)
                : allocateKeepingCurrent(businessId, service, newStart, reservation);

        reservation.schedule(allocation);
        if (!Set.copyOf(allocation.resourceIds()).equals(Set.copyOf(reservation.resourceIds()))) {
            // Hibernate inserts before it deletes: release the old rows first so
            // a resource that stays assigned does not collide with itself.
            reservation.getResources().clear();
            saveAndFlush(reservation);
            reservation.assignResources(allocation.resources());
        }
        Reservation saved = saveAndFlush(reservation);

        log.info("Reservation changed id={} business={} resources={}", id, businessId, saved.resourceIds());
        if (moved) {
            events.publishEvent(ReservationNotificationEvent.rescheduled(payloadFor(business, saved,
                    "Previously: " + previousLocal.toLocalDate() + " at " + previousLocal.toLocalTime())));
        }
        return assembler.toResponse(business, saved);
    }

    private ResourceAllocation allocateKeepingCurrent(Long businessId, BookableService service, Instant start,
                                                      Reservation reservation) {
        Map<Long, List<Long>> current = reservation.getResources().stream()
                .filter(held -> held.getRequirementId() != null)
                .collect(Collectors.groupingBy(ReservationResource::getRequirementId, LinkedHashMap::new,
                        Collectors.mapping(ReservationResource::getResourceId, Collectors.toList())));

        // Keep, per requirement, the resources that are still free at the new
        // time; only the others are reassigned. An unavailable time fails here
        // with the precise reason.
        Map<Long, List<Long>> stillFree = new LinkedHashMap<>();
        for (SlotResponse.SlotRequirement option : availabilityService.optionsAt(businessId, service, start,
                reservation.getNumberOfParticipants(), reservation.getId()).requirements()) {
            List<Long> held = current.get(option.requirementId());
            Set<Long> free = option.resources().stream().map(SlotResponse.ResourceOption::id)
                    .collect(Collectors.toSet());
            if (held != null && free.containsAll(held)) {
                stillFree.put(option.requirementId(), held);
            }
        }
        try {
            return availabilityService.allocate(businessId, service, start, reservation.getNumberOfParticipants(),
                    stillFree, reservation.getId(), false);
        } catch (ApiException ex) {
            if (ex.code() != ErrorCode.INVALID_RESOURCE_SELECTION) {
                throw ex;
            }
            // Kept resources no longer fit the group (e.g. capacity changed): reassign everything.
            return availabilityService.allocate(businessId, service, start, reservation.getNumberOfParticipants(),
                    Map.of(), reservation.getId(), false);
        }
    }

    /** Customer-initiated cancellation, subject to the business's policy. */
    @Transactional
    public PublicReservationResponse cancelByCustomer(UUID reference, String reason) {
        Reservation reservation = repository.findByPublicReference(reference)
                .orElseThrow(() -> ApiException.notFound("No reservation matches that reference"));
        Business business = businessService.requireById(reservation.getBusinessId());
        BusinessSettings settings = businessService.settingsOf(business.getId());

        if (!settings.isAllowCustomerCancellation()) {
            throw ApiException.of(ErrorCode.CANCELLATION_NOT_ALLOWED,
                    "Please contact the business to change this reservation.");
        }
        if (!reservation.getStatus().isActive()) {
            throw ApiException.of(ErrorCode.INVALID_RESERVATION_STATE, "This reservation can no longer be cancelled.");
        }
        if (clock.instant().plus(settings.cancellationDeadline()).isAfter(reservation.getStartTime())) {
            throw ApiException.of(ErrorCode.CANCELLATION_DEADLINE_PASSED,
                    "Reservations can only be cancelled at least " + settings.getCancellationDeadlineHours()
                            + " hours in advance. Please contact the business.");
        }

        applyCancellation(reservation, reason);
        log.info("Reservation cancelled id={} business={} by customer", reservation.getId(), business.getId());
        events.publishEvent(ReservationNotificationEvent.cancelled(payloadFor(business, reservation, reason)));
        return assembler.toPublic(business, reservation, false);
    }

    /** Releases the resources and gives the discount use back. */
    private void applyCancellation(Reservation reservation, String reason) {
        reservation.changeStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(clock.instant());
        reservation.setCancellationReason(blankToNull(reason));
        repository.save(reservation);
        if (reservation.getDiscountId() != null) {
            discountService.release(reservation.getDiscountId());
        }
    }

    // --- helpers -----------------------------------------------------------

    boolean isCancellableByCustomer(Business business, Reservation reservation) {
        BusinessSettings settings = businessService.settingsOf(business.getId());
        return reservation.getStatus().isActive()
                && settings.isAllowCustomerCancellation()
                && !clock.instant().plus(settings.cancellationDeadline()).isAfter(reservation.getStartTime());
    }

    private Reservation requireReservation(Long businessId, Long id) {
        return repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Reservation", id));
    }

    private ReservationNotificationPayload payloadFor(Business business, Reservation reservation, String detail) {
        Customer customer = customerService.requireForBusiness(business.getId(), reservation.getCustomerId());
        BookableService service = serviceCatalog.requireForBusiness(business.getId(), reservation.getServiceId());
        return new ReservationNotificationPayload(
                business.getId(), reservation.getId(), business.getName(), business.getEmail(), business.getPhone(),
                business.getAddress(), business.getTimezone(), customer.fullName(), customer.getEmail(),
                service.getName(), reservation.getNumberOfParticipants(),
                assembler.resourceSummary(business.getId(), reservation),
                reservation.getStartTime(), reservation.getEndTime(),
                reservation.getPublicReference().toString(), detail);
    }

    static Map<Long, List<Long>> selections(List<ResourceSelection> selections) {
        if (selections == null) {
            return Map.of();
        }
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (ResourceSelection selection : selections) {
            if (selection == null || selection.resourceIds() == null || selection.resourceIds().isEmpty()) {
                continue;
            }
            if (result.put(selection.requirementId(),
                    selection.resourceIds().stream().filter(Objects::nonNull).toList()) != null) {
                throw ApiException.of(ErrorCode.INVALID_RESOURCE_SELECTION,
                        "Each requirement can be selected only once.");
            }
        }
        return result;
    }

    private static boolean mentions(Throwable ex, String constraint) {
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(constraint)) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private static Instant toInstant(LocalDateTime localDateTime, ZoneId zone) {
        return localDateTime.atZone(zone).toInstant();
    }

    private static String describe(ReservationStatus status) {
        return status.name().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
