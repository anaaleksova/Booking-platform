package com.bookingsaas.reservation;

import com.bookingsaas.availability.AvailabilityService;
import com.bookingsaas.availability.dto.SlotResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerService;
import com.bookingsaas.reservation.dto.CustomerHistoryResponse;
import com.bookingsaas.reservation.dto.PublicReservationResponse;
import com.bookingsaas.reservation.dto.ReservationResponse;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reading reservations: lists, details, a customer's history and reassignment options. */
@Service
public class ReservationQueryService {

    /** A list request without a range covers this many days around today. */
    private static final int DEFAULT_PAST_DAYS = 30;
    private static final int DEFAULT_FUTURE_DAYS = 60;

    private final ReservationRepository repository;
    private final ReservationViewAssembler assembler;
    private final ReservationAccess access;
    private final ReservationService reservationService;
    private final AvailabilityService availabilityService;
    private final BusinessService businessService;
    private final ServiceCatalogService serviceCatalog;
    private final CustomerService customerService;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public ReservationQueryService(ReservationRepository repository,
                                   ReservationViewAssembler assembler,
                                   ReservationAccess access,
                                   ReservationService reservationService,
                                   AvailabilityService availabilityService,
                                   BusinessService businessService,
                                   ServiceCatalogService serviceCatalog,
                                   CustomerService customerService,
                                   CurrentTenant currentTenant,
                                   Clock clock) {
        this.repository = repository;
        this.assembler = assembler;
        this.access = access;
        this.reservationService = reservationService;
        this.availabilityService = availabilityService;
        this.businessService = businessService;
        this.serviceCatalog = serviceCatalog;
        this.customerService = customerService;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    /** Filter criteria for the reservation list and calendar. All optional. */
    public record Filter(LocalDate from, LocalDate to, ReservationStatus status, Long serviceId,
                         Long customerId, Long resourceId) {
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(Filter filter) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();

        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate from = filter.from() != null ? filter.from()
                : filter.to() != null ? filter.to().minusDays(DEFAULT_PAST_DAYS + DEFAULT_FUTURE_DAYS)
                : today.minusDays(DEFAULT_PAST_DAYS);
        LocalDate to = filter.to() != null ? filter.to() : from.plusDays(DEFAULT_PAST_DAYS + DEFAULT_FUTURE_DAYS);
        if (to.isBefore(from)) {
            throw ApiException.validation("'to' must not be before 'from'.");
        }
        Long resourceId = access.effectiveResourceFilter(businessId, filter.resourceId());

        List<Reservation> reservations = repository.findAll(
                specification(businessId, from.atStartOfDay(zone).toInstant(),
                        to.plusDays(1).atStartOfDay(zone).toInstant(), filter, resourceId),
                Sort.by("startTime").ascending());
        return assembler.toResponses(business, reservations);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long id) {
        Long businessId = currentTenant.businessId();
        Reservation reservation = require(businessId, id);
        access.requireAccess(businessId, reservation);
        return assembler.toResponse(businessService.requireById(businessId), reservation);
    }

    /** Upcoming reservations for the dashboard, soonest first. */
    @Transactional(readOnly = true)
    public List<ReservationResponse> upcoming(int limit) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        return assembler.toResponses(business, repository.findUpcoming(businessId, clock.instant(),
                List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED), PageRequest.of(0, limit)));
    }

    /**
     * What else is free for this reservation at its current time — the choices
     * offered when an admin changes the assigned resources.
     */
    @Transactional(readOnly = true)
    public SlotResponse resourceOptions(Long id) {
        Long businessId = currentTenant.businessId();
        Reservation reservation = require(businessId, id);
        access.requireAccess(businessId, reservation);
        BookableService service = serviceCatalog.requireForBusiness(businessId, reservation.getServiceId());
        return availabilityService.optionsAt(businessId, service, reservation.getStartTime(),
                reservation.getNumberOfParticipants(), reservation.getId());
    }

    /** The customer's own view, reachable only with the unguessable reference. */
    @Transactional(readOnly = true)
    public PublicReservationResponse getByReference(UUID reference) {
        Reservation reservation = repository.findByPublicReference(reference)
                .orElseThrow(() -> ApiException.notFound("No reservation matches that reference"));
        Business business = businessService.requireById(reservation.getBusinessId());
        return assembler.toPublic(business, reservation, reservationService.isCancellableByCustomer(business, reservation));
    }

    @Transactional(readOnly = true)
    public CustomerHistoryResponse customerHistory(Long customerId) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        Customer customer = customerService.requireForBusiness(businessId, customerId);
        List<Reservation> reservations = repository.findByBusinessIdAndCustomerIdOrderByStartTimeDesc(businessId,
                customerId);
        Instant now = clock.instant();

        List<Reservation> kept = reservations.stream()
                .filter(reservation -> reservation.getStatus() != ReservationStatus.CANCELLED)
                .toList();
        List<Reservation> paid = reservations.stream()
                .filter(reservation -> reservation.getStatus().countsTowardRevenue())
                .toList();
        BigDecimal totalSpent = paid.stream().map(Reservation::getFinalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, Long> perService = kept.stream()
                .collect(Collectors.groupingBy(Reservation::getServiceId, Collectors.counting()));
        String favourite = perService.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> serviceCatalog.requireForBusiness(businessId, entry.getKey()).getName())
                .orElse(null);

        return new CustomerHistoryResponse(
                customer.getId(),
                customer.fullName(),
                kept.size(),
                reservations.stream().filter(r -> r.getStatus() == ReservationStatus.COMPLETED).count(),
                reservations.stream().filter(r -> r.getStatus() == ReservationStatus.CANCELLED).count(),
                kept.stream().filter(r -> r.getStatus().isActive() && r.getStartTime().isAfter(now)).count(),
                kept.stream().map(Reservation::getStartTime).filter(start -> !start.isAfter(now))
                        .max(Comparator.naturalOrder()).orElse(null),
                kept.stream().filter(r -> r.getStatus().isActive()).map(Reservation::getStartTime)
                        .filter(start -> start.isAfter(now)).min(Comparator.naturalOrder()).orElse(null),
                totalSpent,
                paid.isEmpty() ? BigDecimal.ZERO
                        : totalSpent.divide(BigDecimal.valueOf(paid.size()), 2, RoundingMode.HALF_UP),
                favourite,
                assembler.toResponses(business, reservations));
    }

    private Reservation require(Long businessId, Long id) {
        return repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Reservation", id));
    }

    private static Specification<Reservation> specification(Long businessId, Instant from, Instant to,
                                                            Filter filter, Long resourceId) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Never optional: the tenant predicate is the first thing added.
            predicates.add(builder.equal(root.get("businessId"), businessId));
            predicates.add(builder.greaterThanOrEqualTo(root.get("startTime"), from));
            predicates.add(builder.lessThan(root.get("startTime"), to));
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter.serviceId() != null) {
                predicates.add(builder.equal(root.get("serviceId"), filter.serviceId()));
            }
            if (filter.customerId() != null) {
                predicates.add(builder.equal(root.get("customerId"), filter.customerId()));
            }
            if (resourceId != null) {
                Subquery<Long> held = query.subquery(Long.class);
                var link = held.from(ReservationResource.class);
                held.select(link.get("reservation").get("id"))
                        .where(builder.equal(link.get("resourceId"), resourceId));
                predicates.add(root.get("id").in(held));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
