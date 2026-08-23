package com.bookingsaas.booking;

import com.bookingsaas.booking.dto.DashboardStatsResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.security.CurrentTenant;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The summary cards on the dashboard home.
 *
 * <p>"Today" means today <em>in the business timezone</em> — a studio in Skopje
 * and one in Chicago must each see their own day, whatever the server thinks.
 */
@Service
public class DashboardService {

    private final BookingRepository repository;
    private final BookingViewAssembler assembler;
    private final BusinessService businessService;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public DashboardService(BookingRepository repository,
                            BookingViewAssembler assembler,
                            BusinessService businessService,
                            CurrentTenant currentTenant,
                            Clock clock) {
        this.repository = repository;
        this.assembler = assembler;
        this.businessService = businessService;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse stats() {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();

        List<Booking> todaysBookings = repository.findAll(
                (root, query, builder) -> builder.and(
                        builder.equal(root.get("businessId"), businessId),
                        builder.greaterThanOrEqualTo(root.get("startDateTime"), dayStart),
                        builder.lessThan(root.get("startDateTime"), dayEnd)),
                Sort.by("startDateTime").ascending());

        long upcoming = repository.countByBusinessIdAndStatusAndStartDateTimeAfter(
                businessId, BookingStatus.CONFIRMED, now);
        long completed = repository.countByBusinessIdAndStatusAndStartDateTimeBetween(
                businessId, BookingStatus.COMPLETED, today.minusDays(30).atStartOfDay(zone).toInstant(), dayEnd);
        long cancelled = repository.countByBusinessIdAndStatusAndStartDateTimeBetween(
                businessId, BookingStatus.CANCELLED, today.minusDays(30).atStartOfDay(zone).toInstant(), dayEnd);

        return new DashboardStatsResponse(
                todaysBookings.stream().filter(booking -> booking.getStatus() != BookingStatus.CANCELLED).count(),
                upcoming,
                completed,
                cancelled,
                BookingService.revenueOf(todaysBookings),
                todaysBookings.stream().map(Booking::getCurrency).filter(java.util.Objects::nonNull)
                        .findFirst().orElse(null),
                assembler.toResponses(business, todaysBookings));
    }
}
