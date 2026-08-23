package com.bookingsaas.booking;

import com.bookingsaas.availability.AvailabilityService;
import com.bookingsaas.availability.SlotAssignment;
import com.bookingsaas.booking.dto.BookingResponse;
import com.bookingsaas.booking.dto.CreateBookingRequest;
import com.bookingsaas.booking.dto.CustomerHistoryResponse;
import com.bookingsaas.booking.dto.PublicBookingRequest;
import com.bookingsaas.booking.dto.PublicBookingResponse;
import com.bookingsaas.booking.dto.RescheduleBookingRequest;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerService;
import com.bookingsaas.notification.BookingNotificationEvent;
import com.bookingsaas.notification.BookingNotificationPayload;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import com.bookingsaas.staff.Staff;
import com.bookingsaas.staff.StaffService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The booking lifecycle, and the only place bookings are written.
 *
 * <p>Double booking is prevented in three layers, deliberately overlapping:
 *
 * <ol>
 *   <li>the availability engine never offers a taken slot;</li>
 *   <li>{@link AvailabilityService#resolve} re-checks the exact time
 *       <em>inside</em> this transaction, so a slot taken since the customer
 *       loaded the page is caught;</li>
 *   <li>PostgreSQL exclusion constraints on the staff and resource block
 *       windows reject the second of two genuinely simultaneous inserts.</li>
 * </ol>
 *
 * <p>Layer 3 is the one that actually holds under concurrency — layers 1 and 2
 * exist to turn a race into a clear message rather than a database error.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository repository;
    private final BookingViewAssembler assembler;
    private final AvailabilityService availabilityService;
    private final BusinessService businessService;
    private final ServiceCatalogService serviceCatalog;
    private final StaffService staffService;
    private final ResourceService resourceService;
    private final CustomerService customerService;
    private final ApplicationEventPublisher events;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public BookingService(BookingRepository repository,
                          BookingViewAssembler assembler,
                          AvailabilityService availabilityService,
                          BusinessService businessService,
                          ServiceCatalogService serviceCatalog,
                          StaffService staffService,
                          ResourceService resourceService,
                          CustomerService customerService,
                          ApplicationEventPublisher events,
                          CurrentTenant currentTenant,
                          Clock clock) {
        this.repository = repository;
        this.assembler = assembler;
        this.availabilityService = availabilityService;
        this.businessService = businessService;
        this.serviceCatalog = serviceCatalog;
        this.staffService = staffService;
        this.resourceService = resourceService;
        this.customerService = customerService;
        this.events = events;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    // --- creation ----------------------------------------------------------

    /** A booking taken by the business. Lead-time rules do not apply. */
    @Transactional
    public BookingResponse createFromDashboard(CreateBookingRequest request) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        BookableService service = serviceCatalog.requireBookable(businessId, request.serviceId());
        Customer customer = resolveCustomer(businessId, request);
        Instant start = toInstant(request.startDateTime(), business.zoneId());

        SlotAssignment assignment = availabilityService.resolve(businessId, service, start,
                request.staffId(), request.resourceId(), null, false);

        Booking booking = build(businessId, service, customer, assignment, BookingSource.DASHBOARD,
                request.customerNotes(), request.internalNotes(), currentTenant.userId());
        Booking saved = persist(booking);

        log.info("Booking created id={} business={} service={} staff={} source=DASHBOARD",
                saved.getId(), businessId, service.getId(), saved.getStaffId());
        publish(BookingNotificationEvent.created(payloadFor(business, saved, null)));
        return assembler.toResponse(business, saved);
    }

    /** A booking made by a customer on the public page. All rules apply. */
    @Transactional
    public PublicBookingResponse createFromPublicPage(String businessSlug, PublicBookingRequest request) {
        Business business = businessService.requireActiveBySlug(businessSlug);
        Long businessId = business.getId();
        BookableService service = serviceCatalog.requireBookable(businessId, request.serviceId());

        Customer customer = customerService.findOrCreate(businessId, request.name(),
                request.phone(), request.email(), null);
        Instant start = toInstant(request.startDateTime(), business.zoneId());

        SlotAssignment assignment = availabilityService.resolve(businessId, service, start,
                request.staffId(), null, null, true);

        Booking booking = build(businessId, service, customer, assignment, BookingSource.PUBLIC,
                request.notes(), null, null);
        Booking saved = persist(booking);

        log.info("Booking created id={} business={} service={} staff={} source=PUBLIC",
                saved.getId(), businessId, service.getId(), saved.getStaffId());
        publish(BookingNotificationEvent.created(payloadFor(business, saved, null)));
        return toPublicResponse(business, saved);
    }

    private Booking build(Long businessId,
                          BookableService service,
                          Customer customer,
                          SlotAssignment assignment,
                          BookingSource source,
                          String customerNotes,
                          String internalNotes,
                          Long actorUserId) {
        Booking booking = new Booking();
        booking.setBusinessId(businessId);
        booking.setServiceId(service.getId());
        booking.setStaffId(assignment.staffId());
        booking.setResourceId(assignment.resourceId());
        booking.setCustomerId(customer.getId());
        booking.setStartDateTime(assignment.start());
        booking.setEndDateTime(assignment.end());
        booking.setBlockStartDateTime(assignment.blockStart());
        booking.setBlockEndDateTime(assignment.blockEnd());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCustomerNotes(blankToNull(customerNotes));
        booking.setInternalNotes(blankToNull(internalNotes));
        // Snapshot: editing the service's price later must not restate history.
        booking.setPrice(service.getPrice());
        booking.setCurrency(service.getCurrency());
        booking.setSource(source);
        booking.setCreatedByUserId(actorUserId);
        return booking;
    }

    /**
     * Writes the booking, translating an exclusion-constraint violation into
     * the same 409 the pre-check would have produced. This is the branch two
     * simultaneous customers actually hit.
     */
    private Booking persist(Booking booking) {
        try {
            return repository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                log.info("Concurrent booking rejected by exclusion constraint for business {}",
                        booking.getBusinessId());
                throw ApiException.of(ErrorCode.BOOKING_NOT_AVAILABLE,
                        "The selected time is no longer available.");
            }
            throw ex;
        }
    }

    private static boolean isOverlapViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("excl_bookings_staff_overlap")
                    || message.contains("excl_bookings_resource_overlap"))) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private Customer resolveCustomer(Long businessId, CreateBookingRequest request) {
        if (request.customerId() != null) {
            return customerService.requireForBusiness(businessId, request.customerId());
        }
        if (request.customer() == null) {
            throw ApiException.validation("Select an existing customer or provide new customer details.");
        }
        return customerService.findOrCreate(businessId, request.customer().name(),
                request.customer().phone(), request.customer().email(), null);
    }

    // --- reading -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BookingResponse> list(LocalDate from, LocalDate to, Long staffId, Long customerId,
                                      BookingStatus status) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();

        Instant fromInstant = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        Long effectiveStaffId = restrictToOwnScheduleIfStaff(businessId, staffId);

        List<Booking> bookings = repository.findAll(
                filter(businessId, fromInstant, toInstant, effectiveStaffId, customerId, status),
                Sort.by("startDateTime").ascending());
        return assembler.toResponses(business, bookings);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        Booking booking = requireBooking(businessId, id);
        requireAccess(businessId, booking);
        return assembler.toResponse(business, booking);
    }

    /** The customer's own view, reachable only with the unguessable reference. */
    @Transactional(readOnly = true)
    public PublicBookingResponse getByReference(UUID reference) {
        Booking booking = repository.findByPublicReference(reference)
                .orElseThrow(() -> ApiException.notFound("No booking matches that reference"));
        return toPublicResponse(businessService.requireById(booking.getBusinessId()), booking);
    }

    @Transactional(readOnly = true)
    public CustomerHistoryResponse customerHistory(Long customerId) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        Customer customer = customerService.requireForBusiness(businessId, customerId);

        List<Booking> bookings = repository
                .findByBusinessIdAndCustomerIdOrderByStartDateTimeDesc(businessId, customerId);
        Instant now = clock.instant();

        Instant last = bookings.stream()
                .filter(booking -> booking.getStartDateTime().isBefore(now))
                .map(Booking::getStartDateTime)
                .max(Comparator.naturalOrder())
                .orElse(null);
        Instant next = bookings.stream()
                .filter(booking -> booking.getStatus().isActive() && booking.getStartDateTime().isAfter(now))
                .map(Booking::getStartDateTime)
                .min(Comparator.naturalOrder())
                .orElse(null);

        List<Booking> revenueBookings = bookings.stream()
                .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
                .toList();
        BigDecimal totalSpent = revenueOf(bookings);
        BigDecimal averageBookingValue = revenueBookings.isEmpty() ? BigDecimal.ZERO
                : totalSpent.divide(BigDecimal.valueOf(revenueBookings.size()), 2, RoundingMode.HALF_UP);
        Long favouriteServiceId = revenueBookings.stream()
                .collect(Collectors.groupingBy(Booking::getServiceId, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        String favouriteServiceName = favouriteServiceId == null ? null
                : serviceCatalog.requireForBusiness(businessId, favouriteServiceId).getName();

        return new CustomerHistoryResponse(
                customer.getId(),
                customer.getName(),
                bookings.size(),
                count(bookings, BookingStatus.COMPLETED),
                count(bookings, BookingStatus.CANCELLED),
                count(bookings, BookingStatus.NO_SHOW),
                bookings.stream()
                        .filter(booking -> booking.getStatus().isActive()
                                && booking.getStartDateTime().isAfter(now))
                        .count(),
                last,
                next,
                totalSpent,
                averageBookingValue,
                favouriteServiceId,
                favouriteServiceName,
                assembler.toResponses(business, bookings));
    }

    private static long count(List<Booking> bookings, BookingStatus status) {
        return bookings.stream().filter(booking -> booking.getStatus() == status).count();
    }

    // --- lifecycle ---------------------------------------------------------

    @Transactional
    public BookingResponse reschedule(Long id, RescheduleBookingRequest request) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        Booking booking = requireBooking(businessId, id);
        requireAccess(businessId, booking);

        if (!booking.getStatus().isActive()) {
            throw ApiException.of(ErrorCode.INVALID_BOOKING_STATE,
                    "Only an active booking can be rescheduled.");
        }

        BookableService service = serviceCatalog.requireForBusiness(businessId, booking.getServiceId());
        Instant newStart = toInstant(request.startDateTime(), business.zoneId());
        LocalDateTime previousLocal = LocalDateTime.ofInstant(booking.getStartDateTime(), business.zoneId());

        // The booking must not block its own move, hence the exclusion.
        SlotAssignment assignment = availabilityService.resolve(businessId, service, newStart,
                request.staffId() != null ? request.staffId() : booking.getStaffId(),
                request.resourceId() != null ? request.resourceId() : booking.getResourceId(),
                booking.getId(), false);

        booking.setStaffId(assignment.staffId());
        booking.setResourceId(assignment.resourceId());
        booking.setStartDateTime(assignment.start());
        booking.setEndDateTime(assignment.end());
        booking.setBlockStartDateTime(assignment.blockStart());
        booking.setBlockEndDateTime(assignment.blockEnd());
        Booking saved = persist(booking);

        log.info("Booking rescheduled id={} business={}", saved.getId(), businessId);
        publish(BookingNotificationEvent.rescheduled(
                payloadFor(business, saved, "Previously: " + previousLocal.toLocalDate()
                        + " at " + previousLocal.toLocalTime())));
        return assembler.toResponse(business, saved);
    }

    @Transactional
    public BookingResponse cancel(Long id, String reason) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        Booking booking = requireBooking(businessId, id);
        requireAccess(businessId, booking);

        // The business may always cancel — the deadline protects the business
        // from late customer cancellations, not the other way round.
        Booking saved = applyCancellation(booking, reason);
        log.info("Booking cancelled id={} business={} by user={}", saved.getId(), businessId,
                currentTenant.userId());
        publish(BookingNotificationEvent.cancelled(payloadFor(business, saved, reason)));
        return assembler.toResponse(business, saved);
    }

    /** Customer-initiated cancellation, subject to the tenant's policy. */
    @Transactional
    public PublicBookingResponse cancelByReference(UUID reference, String reason) {
        Booking booking = repository.findByPublicReference(reference)
                .orElseThrow(() -> ApiException.notFound("No booking matches that reference"));
        Business business = businessService.requireById(booking.getBusinessId());
        BusinessSettings settings = businessService.settingsOf(booking.getBusinessId());

        if (!settings.isAllowCustomerCancellation()) {
            throw ApiException.of(ErrorCode.CANCELLATION_NOT_ALLOWED,
                    "Please contact the business to change this booking.");
        }
        if (clock.instant().plus(settings.cancellationDeadline()).isAfter(booking.getStartDateTime())) {
            throw ApiException.of(ErrorCode.CANCELLATION_DEADLINE_PASSED,
                    "Bookings can only be cancelled at least " + settings.getCancellationDeadlineHours()
                            + " hours in advance. Please contact the business.");
        }

        Booking saved = applyCancellation(booking, reason);
        log.info("Booking cancelled id={} business={} by customer", saved.getId(), business.getId());
        publish(BookingNotificationEvent.cancelled(payloadFor(business, saved, reason)));
        return toPublicResponse(business, saved);
    }

    private Booking applyCancellation(Booking booking, String reason) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.of(ErrorCode.INVALID_BOOKING_STATE, "This booking is already cancelled.");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw ApiException.of(ErrorCode.INVALID_BOOKING_STATE, "A completed booking cannot be cancelled.");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(clock.instant());
        booking.setCancellationReason(blankToNull(reason));
        return repository.save(booking);
    }

    @Transactional
    public BookingResponse markCompleted(Long id) {
        return changeStatus(id, BookingStatus.COMPLETED);
    }

    @Transactional
    public BookingResponse markNoShow(Long id) {
        return changeStatus(id, BookingStatus.NO_SHOW);
    }

    private BookingResponse changeStatus(Long id, BookingStatus target) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        Booking booking = requireBooking(businessId, id);
        requireAccess(businessId, booking);

        if (!booking.getStatus().isActive()) {
            throw ApiException.of(ErrorCode.INVALID_BOOKING_STATE,
                    "Only an active booking can be marked as " + target.name().toLowerCase().replace('_', ' ') + ".");
        }
        booking.setStatus(target);
        Booking saved = repository.save(booking);
        return assembler.toResponse(business, saved);
    }

    // --- authorization -----------------------------------------------------

    private Booking requireBooking(Long businessId, Long id) {
        return repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Booking", id));
    }

    /**
     * Owners see the whole business; a staff member sees only their own
     * appointments. The tenant check has already happened by this point — this
     * is the second, role-level filter.
     */
    private void requireAccess(Long businessId, Booking booking) {
        if (currentTenant.isOwner()) {
            return;
        }
        Long ownStaffId = staffService.staffRecordOf(businessId, currentTenant.userId())
                .map(Staff::getId)
                .orElse(null);
        if (ownStaffId == null || !ownStaffId.equals(booking.getStaffId())) {
            throw ApiException.accessDenied("You can only manage your own bookings.");
        }
    }

    private Long restrictToOwnScheduleIfStaff(Long businessId, Long requestedStaffId) {
        if (currentTenant.isOwner()) {
            return requestedStaffId;
        }
        Long ownStaffId = staffService.staffRecordOf(businessId, currentTenant.userId())
                .map(Staff::getId)
                .orElseThrow(() -> ApiException.accessDenied("No staff profile is linked to this account."));
        if (requestedStaffId != null && !requestedStaffId.equals(ownStaffId)) {
            throw ApiException.accessDenied("You can only view your own bookings.");
        }
        return ownStaffId;
    }

    private static Specification<Booking> filter(Long businessId, Instant from, Instant to,
                                                 Long staffId, Long customerId, BookingStatus status) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Never optional: the tenant predicate is the first thing added.
            predicates.add(builder.equal(root.get("businessId"), businessId));
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("startDateTime"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get("startDateTime"), to));
            }
            if (staffId != null) {
                predicates.add(builder.equal(root.get("staffId"), staffId));
            }
            if (customerId != null) {
                predicates.add(builder.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    // --- mapping helpers ---------------------------------------------------

    private PublicBookingResponse toPublicResponse(Business business, Booking booking) {
        ZoneId zone = business.zoneId();
        BusinessSettings settings = businessService.settingsOf(business.getId());
        BookableService service = serviceCatalog.requireForBusiness(business.getId(), booking.getServiceId());
        String staffName = Optional.ofNullable(booking.getStaffId())
                .map(id -> staffService.requireForBusiness(business.getId(), id).getName())
                .orElse(null);

        boolean cancellable = booking.getStatus().isActive()
                && settings.isAllowCustomerCancellation()
                && !clock.instant().plus(settings.cancellationDeadline()).isAfter(booking.getStartDateTime());

        return new PublicBookingResponse(
                booking.getPublicReference(),
                business.getName(),
                business.getSlug(),
                business.getAddress(),
                business.getPhone(),
                service.getName(),
                staffName,
                booking.getStartDateTime(),
                booking.getEndDateTime(),
                LocalDateTime.ofInstant(booking.getStartDateTime(), zone),
                LocalDateTime.ofInstant(booking.getEndDateTime(), zone),
                business.getTimezone(),
                booking.getStatus().name(),
                booking.getPrice(),
                booking.getCurrency(),
                customerService.requireForBusiness(business.getId(), booking.getCustomerId()).getName(),
                cancellable);
    }

    private BookingNotificationPayload payloadFor(Business business, Booking booking, String detail) {
        Customer customer = customerService.requireForBusiness(business.getId(), booking.getCustomerId());
        BookableService service = serviceCatalog.requireForBusiness(business.getId(), booking.getServiceId());
        String staffName = Optional.ofNullable(booking.getStaffId())
                .map(id -> staffService.requireForBusiness(business.getId(), id).getName())
                .orElse(null);
        String resourceName = Optional.ofNullable(booking.getResourceId())
                .map(id -> resourceService.requireForBusiness(business.getId(), id))
                .map(Resource::getName)
                .orElse(null);

        return new BookingNotificationPayload(
                business.getId(), booking.getId(), business.getName(), business.getEmail(), business.getPhone(),
                business.getAddress(), business.getTimezone(), customer.getName(), customer.getEmail(),
                service.getName(), staffName, resourceName, booking.getStartDateTime(), booking.getEndDateTime(),
                booking.getPublicReference().toString(), detail);
    }

    private void publish(BookingNotificationEvent event) {
        events.publishEvent(event);
    }

    private static Instant toInstant(LocalDateTime localDateTime, ZoneId zone) {
        return localDateTime.atZone(zone).toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Total value of the non-cancelled bookings in a list. */
    static BigDecimal revenueOf(List<Booking> bookings) {
        return bookings.stream()
                .filter(booking -> booking.getStatus() != BookingStatus.CANCELLED)
                .map(Booking::getPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
