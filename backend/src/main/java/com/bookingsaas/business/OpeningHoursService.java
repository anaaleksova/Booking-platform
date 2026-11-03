package com.bookingsaas.business;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.SetupAreaEdited;
import com.bookingsaas.common.security.CurrentTenant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The business's opening hours, replaced as a whole week at a time. */
@Service
public class OpeningHoursService {

    private final BusinessHourRepository repository;
    private final CurrentTenant currentTenant;
    private final ApplicationEventPublisher events;

    public OpeningHoursService(BusinessHourRepository repository, CurrentTenant currentTenant,
                               ApplicationEventPublisher events) {
        this.repository = repository;
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

    static DayInterval toInterval(BusinessHour hour) {
        return new DayInterval((int) hour.getDayOfWeek(), hour.getStartTime(), hour.getEndTime());
    }
}
