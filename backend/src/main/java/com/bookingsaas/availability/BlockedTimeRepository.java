package com.bookingsaas.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BlockedTimeRepository extends JpaRepository<BlockedTime, Long> {

    Optional<BlockedTime> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Everything that touches {@code [from, to)} — the standard overlap
     * predicate {@code start < to AND end > from}, matching
     * {@link com.bookingsaas.common.domain.TimeRange#overlaps}.
     */
    @Query("""
           select b from BlockedTime b
           where b.businessId = :businessId
             and b.startTime < :to
             and b.endTime > :from
           order by b.startTime
           """)
    List<BlockedTime> findOverlapping(@Param("businessId") Long businessId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);
}
