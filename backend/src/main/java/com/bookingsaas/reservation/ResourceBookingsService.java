package com.bookingsaas.reservation;

import com.bookingsaas.availability.spi.ScheduleConflict;
import com.bookingsaas.availability.spi.ScheduleConflictProvider;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.reservation.dto.RescheduleRequest;
import com.bookingsaas.reservation.dto.ReservationResponse;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The bookings a resource still holds, and moving them off it. A deactivated
 * resource (or employee) keeps its bookings until the owner reassigns them:
 * nothing is taken from a customer silently.
 */
@Service
public class ResourceBookingsService {

    private static final Logger log = LoggerFactory.getLogger(ResourceBookingsService.class);
    private static final List<ReservationStatus> ACTIVE = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
    /** Far enough ahead to cover any booking window a business can set. */
    private static final Duration HORIZON = Duration.ofDays(3660);

    private final ScheduleConflictProvider conflicts;
    private final ReservationRepository repository;
    private final ReservationService reservationService;
    private final ResourceService resourceService;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public ResourceBookingsService(ScheduleConflictProvider conflicts, ReservationRepository repository,
                                   ReservationService reservationService, ResourceService resourceService,
                                   CurrentTenant currentTenant, Clock clock) {
        this.conflicts = conflicts;
        this.repository = repository;
        this.reservationService = reservationService;
        this.resourceService = resourceService;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    /** Bookings not yet over that hold the resource, soonest first. */
    public List<ScheduleConflict> upcoming(Long resourceId) {
        Long businessId = currentTenant.businessId();
        resourceService.requireForBusiness(businessId, resourceId);
        Instant now = clock.instant();
        return conflicts.holding(businessId, resourceId, now, now.plus(HORIZON));
    }

    /** Inactive resources that still have upcoming bookings, with how many. */
    public List<StrandedBookings> stranded() {
        return repository.countStranded(currentTenant.businessId(), clock.instant(), ACTIVE);
    }

    public record FailedReassignment(ScheduleConflict booking, String reason) {
    }

    /** @param reassigned the bookings that moved, with the resources they hold now */
    public record ReassignmentResult(List<ScheduleConflict> reassigned, List<FailedReassignment> failed) {
    }

    /**
     * Gives every upcoming booking of a deactivated resource to free ones at
     * the same time. Each booking is moved in its own transaction, so one
     * that cannot be placed (nothing else free) does not hold back the rest;
     * those are reported for the owner to move or cancel by hand.
     */
    public ReassignmentResult reassignAway(Long resourceId) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Resource resource = resourceService.requireForBusiness(businessId, resourceId);
        if (resource.isActive()) {
            throw ApiException.validation("Deactivate " + resource.getName()
                    + " first: its bookings are only moved to other resources once it is no longer used.");
        }
        List<ScheduleConflict> reassigned = new ArrayList<>();
        List<FailedReassignment> failed = new ArrayList<>();
        for (ScheduleConflict booking : upcoming(resourceId)) {
            try {
                // No selection: keeps whatever is still free and replaces the rest — this resource included.
                ReservationResponse moved = reservationService.reschedule(booking.reservationId(),
                        new RescheduleRequest(booking.localStartTime(), null));
                reassigned.add(new ScheduleConflict(moved.id(), moved.customerName(), moved.serviceName(),
                        moved.numberOfParticipants(), moved.startTime(), moved.endTime(), moved.localStartTime(),
                        moved.localEndTime(),
                        moved.resources().stream().map(ReservationResponse.AssignedResource::name).toList()));
            } catch (ApiException ex) {
                failed.add(new FailedReassignment(booking, ex.getMessage()));
            }
        }
        log.info("Reassigned bookings away from resource {} business={}: {} moved, {} left", resourceId, businessId,
                reassigned.size(), failed.size());
        return new ReassignmentResult(reassigned, failed);
    }
}
