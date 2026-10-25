package com.bookingsaas.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BusinessEventRepository extends JpaRepository<BusinessEvent, Long> {

    List<BusinessEvent> findByBusinessIdOrderByDateDescStartTimeDesc(Long businessId);

    /** Candidates for the booking page today; the time of day is checked by the caller. */
    List<BusinessEvent> findByBusinessIdAndShowFromLessThanEqualAndShowUntilGreaterThanEqualOrderByDateAscStartTimeAsc(
            Long businessId, LocalDate showFromOnOrBefore, LocalDate showUntilOnOrAfter);

    Optional<BusinessEvent> findByIdAndBusinessId(Long id, Long businessId);
}
