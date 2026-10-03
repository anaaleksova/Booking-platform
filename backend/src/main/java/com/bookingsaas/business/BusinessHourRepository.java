package com.bookingsaas.business;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessHourRepository extends JpaRepository<BusinessHour, Long> {

    List<BusinessHour> findByBusinessIdOrderByDayOfWeekAscStartTimeAsc(Long businessId);

    void deleteByBusinessId(Long businessId);
}
