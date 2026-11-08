package com.bookingsaas.business;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SpecialDayRepository extends JpaRepository<SpecialDay, Long> {

    Optional<SpecialDay> findByBusinessIdAndDay(Long businessId, LocalDate day);

    /** Inclusive on both ends. */
    List<SpecialDay> findByBusinessIdAndDayBetweenOrderByDay(Long businessId, LocalDate from, LocalDate to);
}
