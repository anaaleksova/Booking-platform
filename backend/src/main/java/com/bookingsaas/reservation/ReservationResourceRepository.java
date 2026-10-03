package com.bookingsaas.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReservationResourceRepository extends JpaRepository<ReservationResource, Long> {

    /**
     * Resource occupancy touching {@code [from, to)}. Filtered on the block
     * window, so buffers count as occupied. The excluded id lets a reservation
     * being moved ignore itself; callers pass a sentinel rather than null to
     * keep the parameter's type unambiguous.
     */
    @Query("""
           select rr from ReservationResource rr
           where rr.businessId = :businessId
             and rr.occupying = true
             and rr.blockStartTime < :to
             and rr.blockEndTime > :from
             and rr.reservation.id <> :excludeReservationId
           """)
    List<ReservationResource> findOccupying(@Param("businessId") Long businessId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to,
                                            @Param("excludeReservationId") Long excludeReservationId);
}
