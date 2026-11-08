package com.bookingsaas.business;

import com.bookingsaas.business.dto.SpecialHours;
import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.SetupAreaEdited;
import com.bookingsaas.common.security.CurrentTenant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The business's opening hours: the week, replaced as a whole, and special
 * dates whose own hours replace the week's on that date.
 */
@Service
public class OpeningHoursService {

    private final BusinessHourRepository repository;
    private final SpecialDayRepository specialDays;
    private final CurrentTenant currentTenant;
    private final ApplicationEventPublisher events;

    public OpeningHoursService(BusinessHourRepository repository, SpecialDayRepository specialDays,
                               CurrentTenant currentTenant, ApplicationEventPublisher events) {
        this.repository = repository;
        this.specialDays = specialDays;
        this.currentTenant = currentTenant;
        this.events = events;
    }

    /** The business's week as plain intervals — what the availability engine consumes. */
    @Transactional(readOnly = true)
    public List<DayInterval> intervalsOf(Long businessId) {
        return repository.findByBusinessIdOrderByDayOfWeekAscStartTimeAsc(businessId).stream()
                .map(OpeningHoursService::toInterval)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DayInterval> current() {
        return intervalsOf(currentTenant.businessId());
    }

    @Transactional
    public List<DayInterval> replace(List<DayInterval> intervals) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        DayInterval.validate(intervals);

        repository.deleteByBusinessId(businessId);
        repository.flush();
        if (intervals != null && !intervals.isEmpty()) {
            repository.saveAll(intervals.stream()
                    .map(interval -> new BusinessHour(businessId, interval.dayOfWeek(),
                            interval.startTime(), interval.endTime()))
                    .toList());
        }
        events.publishEvent(new SetupAreaEdited(businessId, SetupAreaEdited.Area.HOURS));
        return current();
    }

    /** Seeds a new business's week (its template's) so it is bookable at once. Never overwrites hours. */
    @Transactional
    public void applyWeek(Long businessId, List<DayInterval> intervals) {
        if (!repository.findByBusinessIdOrderByDayOfWeekAscStartTimeAsc(businessId).isEmpty()) {
            return;
        }
        DayInterval.validate(intervals);
        repository.saveAll(intervals.stream()
                .map(interval -> new BusinessHour(businessId, interval.dayOfWeek(),
                        interval.startTime(), interval.endTime()))
                .toList());
    }

    // --- special dates --------------------------------------------------------

    /**
     * Special dates in {@code [from, to]} (inclusive), each with the hours that
     * replace the weekly ones that day; an empty list means closed.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, List<DayInterval>> specialHoursBetween(Long businessId, LocalDate from, LocalDate to) {
        Map<LocalDate, List<DayInterval>> result = new HashMap<>();
        specialDays.findByBusinessIdAndDayBetweenOrderByDay(businessId, from, to)
                .forEach(day -> result.put(day.getDay(), day.intervals()));
        return result;
    }

    /** Special dates from {@code from} for {@code days} days, soonest first: what customers are shown. */
    @Transactional(readOnly = true)
    public List<SpecialHours> upcomingSpecialHours(Long businessId, LocalDate from, int days) {
        return specialDays.findByBusinessIdAndDayBetweenOrderByDay(businessId, from, from.plusDays(days)).stream()
                .map(SpecialHours::from)
                .toList();
    }

    /** Sets one date's hours, replacing what the date had. No spans closes it for the day. */
    @Transactional
    public SpecialDay saveSpecialDay(LocalDate date, List<SpecialHours.Span> spans, String note) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        List<DayInterval> intervals = toIntervals(date, spans);

        SpecialDay day = specialDays.findByBusinessIdAndDay(businessId, date).orElseGet(SpecialDay::new);
        day.setBusinessId(businessId);
        day.setDay(date);
        day.setNote(note == null || note.isBlank() ? null : note.trim());
        day.getHours().clear();
        intervals.forEach(interval -> day.getHours().add(new SpecialDay.Hours(interval.startTime(), interval.endTime())));
        day.getHours().sort(Comparator.comparing(SpecialDay.Hours::getStartTime));
        return specialDays.save(day);
    }

    /** Back to the weekly hours on that date. */
    @Transactional
    public void deleteSpecialDay(LocalDate date) {
        currentTenant.requireOwner();
        specialDays.findByBusinessIdAndDay(currentTenant.businessId(), date).ifPresent(specialDays::delete);
    }

    /** The spans as intervals on the date's weekday, validated like the weekly hours. */
    public static List<DayInterval> toIntervals(LocalDate date, List<SpecialHours.Span> spans) {
        int dayOfWeek = date.getDayOfWeek().getValue();
        List<DayInterval> intervals = spans == null ? List.of() : spans.stream()
                .map(span -> span == null ? null : new DayInterval(dayOfWeek, span.startTime(), span.endTime()))
                .toList();
        DayInterval.validate(intervals);
        return intervals;
    }

    static DayInterval toInterval(BusinessHour hour) {
        return new DayInterval((int) hour.getDayOfWeek(), hour.getStartTime(), hour.getEndTime());
    }
}
