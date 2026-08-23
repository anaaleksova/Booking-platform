package com.bookingsaas.business;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkingHourRepository extends JpaRepository<WorkingHour, Long> {

    List<WorkingHour> findByBusinessIdOrderByDayOfWeekAscStartTimeAsc(Long businessId);

    void deleteByBusinessId(Long businessId);
}
