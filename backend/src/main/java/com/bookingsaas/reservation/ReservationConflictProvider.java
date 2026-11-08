package com.bookingsaas.reservation;

import com.bookingsaas.availability.spi.ScheduleConflict;
import com.bookingsaas.availability.spi.ScheduleConflictProvider;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.reservation.dto.ReservationResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** The reservation module's answer to "which bookings does this schedule change hit?". */
@Component
public class ReservationConflictProvider implements ScheduleConflictProvider {

    private static final List<ReservationStatus> ACTIVE = List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final ReservationRepository repository;
    private final ReservationViewAssembler assembler;
    private final BusinessService businessService;
    private final Clock clock;

    public ReservationConflictProvider(ReservationRepository repository, ReservationViewAssembler assembler,
                                       BusinessService businessService, Clock clock) {
        this.repository = repository;
        this.assembler = assembler;
        this.businessService = businessService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleConflict> holding(Long businessId, Long resourceId, Instant from, Instant to) {
        return toConflicts(businessId,
                repository.findHoldingResource(businessId, resourceId, from, to, clock.instant(), ACTIVE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleConflict> during(Long businessId, Instant from, Instant to) {
        return toConflicts(businessId, repository.findDuring(businessId, from, to, clock.instant(), ACTIVE));
    }

    private List<ScheduleConflict> toConflicts(Long businessId, List<Reservation> reservations) {
        if (reservations.isEmpty()) {
            return List.of();
        }
        Business business = businessService.requireById(businessId);
        return assembler.toResponses(business, reservations).stream()
                .map(r -> new ScheduleConflict(r.id(), r.customerName(), r.serviceName(), r.numberOfParticipants(),
                        r.startTime(), r.endTime(), r.localStartTime(), r.localEndTime(),
                        r.resources().stream().map(ReservationResponse.AssignedResource::name).toList()))
                .toList();
    }
}
