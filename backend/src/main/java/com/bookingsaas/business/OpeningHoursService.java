package com.bookingsaas.business;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.security.CurrentTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/** The business's opening hours, replaced as a whole week at a time. */
@Service
public class OpeningHoursService {

    private final BusinessHourRepository repository;
    private final CurrentTenant currentTenant;

    public OpeningHoursService(BusinessHourRepository repository, CurrentTenant currentTenant) {
        this.repository = repository;
        this.currentTenant = currentTenant;
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
        return current();
    }

    /** Seeds a sensible Mon–Fri week so a new business is bookable at once. */
    @Transactional
    public void applyDefaultWeek(Long businessId) {
        if (!repository.findByBusinessIdOrderByDayOfWeekAscStartTimeAsc(businessId).isEmpty()) {
            return;
        }
        repository.saveAll(java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(day -> new BusinessHour(businessId, day, LocalTime.of(9, 0), LocalTime.of(17, 0)))
                .toList());
    }

    static DayInterval toInterval(BusinessHour hour) {
        return new DayInterval((int) hour.getDayOfWeek(), hour.getStartTime(), hour.getEndTime());
    }
}
