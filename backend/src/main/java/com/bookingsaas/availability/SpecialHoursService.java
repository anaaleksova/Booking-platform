package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.SpecialDayRequest;
import com.bookingsaas.availability.dto.SpecialDayResponse;
import com.bookingsaas.availability.spi.ScheduleConflict;
import com.bookingsaas.availability.spi.ScheduleConflictProvider;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.OpeningHoursService;
import com.bookingsaas.business.dto.SpecialHours;
import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.TimeRange;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Special dates in the admin panel. Like a block, a date's new hours never
 * move bookings: the ones left outside them are reported for the owner to
 * move or cancel, while new bookings already follow the new hours.
 */
@Service
public class SpecialHoursService {

    /** How far ahead the admin list reaches. */
    private static final int LIST_DAYS = 366;

    private final OpeningHoursService openingHours;
    private final BusinessService businessService;
    private final ScheduleConflictProvider conflictProvider;
    private final CurrentTenant currentTenant;

    public SpecialHoursService(OpeningHoursService openingHours, BusinessService businessService,
                               ScheduleConflictProvider conflictProvider, CurrentTenant currentTenant) {
        this.openingHours = openingHours;
        this.businessService = businessService;
        this.conflictProvider = conflictProvider;
        this.currentTenant = currentTenant;
    }

    @Transactional(readOnly = true)
    public List<SpecialDayResponse> upcoming() {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        ZoneId zone = businessService.zoneOf(businessId);
        return openingHours.upcomingSpecialHours(businessId, LocalDate.now(zone), LIST_DAYS).stream()
                .map(day -> SpecialDayResponse.from(day, conflicts(businessId, zone, day.date(),
                        OpeningHoursService.toIntervals(day.date(), day.hours()))))
                .toList();
    }

    /** The bookings these hours would leave outside, before they are saved. */
    @Transactional(readOnly = true)
    public List<ScheduleConflict> preview(LocalDate date, SpecialDayRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        return conflicts(businessId, businessService.zoneOf(businessId), date,
                OpeningHoursService.toIntervals(date, request.hours()));
    }

    @Transactional
    public SpecialDayResponse save(LocalDate date, SpecialDayRequest request) {
        Long businessId = currentTenant.businessId();
        ZoneId zone = businessService.zoneOf(businessId);
        if (date.isBefore(LocalDate.now(zone))) {
            throw ApiException.validation("Special hours can only be set for today or later.");
        }
        SpecialHours saved = SpecialHours.from(openingHours.saveSpecialDay(date, request.hours(), request.note()));
        return SpecialDayResponse.from(saved, conflicts(businessId, zone, date,
                OpeningHoursService.toIntervals(date, saved.hours())));
    }

    @Transactional
    public void delete(LocalDate date) {
        openingHours.deleteSpecialDay(date);
    }

    /** Bookings starting that date whose appointment is not wholly inside the date's hours. */
    private List<ScheduleConflict> conflicts(Long businessId, ZoneId zone, LocalDate date, List<DayInterval> hours) {
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<TimeRange> open = new ArrayList<>();
        for (DayInterval h : hours) {
            Instant start = date.atTime(h.startTime()).atZone(zone).toInstant();
            Instant end = date.atTime(h.endTime()).atZone(zone).toInstant();
            if (start.isBefore(end)) {          // a DST gap can swallow a short span
                open.add(TimeRange.of(start, end));
            }
        }
        List<TimeRange> windows = TimeRange.merge(open);
        return conflictProvider.during(businessId, dayStart, dayEnd).stream()
                .filter(c -> !c.startTime().isBefore(dayStart))
                .filter(c -> !TimeRange.of(c.startTime(), c.endTime()).containedInAny(windows))
                .toList();
    }
}
