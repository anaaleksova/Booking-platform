package com.bookingsaas.business;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.security.CurrentTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/** The business's opening hours, replaced as a whole week at a time. */
@Service
public class WorkingHoursService {

    private final WorkingHourRepository repository;
    private final CurrentTenant currentTenant;

    public WorkingHoursService(WorkingHourRepository repository, CurrentTenant currentTenant) {
        this.repository = repository;
        this.currentTenant = currentTenant;
    }

    @Transactional(readOnly = true)
    public List<WorkingHour> forBusiness(Long businessId) {
        return repository.findByBusinessIdOrderByDayOfWeekAscStartTimeAsc(businessId);
    }

    /** The business's week as plain intervals — what the availability engine consumes. */
    @Transactional(readOnly = true)
    public List<DayInterval> intervalsOf(Long businessId) {
        return forBusiness(businessId).stream()
                .map(WorkingHoursService::toInterval)
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
                    .map(interval -> new WorkingHour(businessId, interval.dayOfWeek(),
                            interval.startTime(), interval.endTime()))
                    .toList());
        }
        return current();
    }

    /** Seeds a sensible Mon–Fri week so a new business is bookable at once. */
    @Transactional
    public void applyDefaultWeek(Long businessId) {
        if (!repository.findByBusinessIdOrderByDayOfWeekAscStartTimeAsc(businessId).isEmpty()) {
            return;
        }
        repository.saveAll(List.of(
                new WorkingHour(businessId, 1, LocalTime.of(9, 0), LocalTime.of(17, 0)),
                new WorkingHour(businessId, 2, LocalTime.of(9, 0), LocalTime.of(17, 0)),
                new WorkingHour(businessId, 3, LocalTime.of(9, 0), LocalTime.of(17, 0)),
                new WorkingHour(businessId, 4, LocalTime.of(9, 0), LocalTime.of(17, 0)),
                new WorkingHour(businessId, 5, LocalTime.of(9, 0), LocalTime.of(17, 0))));
    }

    static DayInterval toInterval(WorkingHour hour) {
        return new DayInterval((int) hour.getDayOfWeek(), hour.getStartTime(), hour.getEndTime());
    }
}
