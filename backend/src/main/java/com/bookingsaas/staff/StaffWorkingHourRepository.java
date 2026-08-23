package com.bookingsaas.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StaffWorkingHourRepository extends JpaRepository<StaffWorkingHour, Long> {

    List<StaffWorkingHour> findByStaffIdOrderByDayOfWeekAscStartTimeAsc(Long staffId);

    List<StaffWorkingHour> findByBusinessIdAndStaffIdIn(Long businessId, Collection<Long> staffIds);

    void deleteByStaffId(Long staffId);
}
