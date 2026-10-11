package com.bookingsaas.reservation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    @EntityGraph(attributePaths = "resources")
    Optional<Reservation> findByIdAndBusinessId(Long id, Long businessId);

    @EntityGraph(attributePaths = "resources")
    Optional<Reservation> findByPublicReference(UUID publicReference);

    @EntityGraph(attributePaths = "resources")
    List<Reservation> findByBusinessIdAndCustomerIdOrderByStartTimeDesc(Long businessId, Long customerId);

    /** Upcoming reservations for the dashboard, soonest first. */
    @Query("""
           select r from Reservation r
           where r.businessId = :businessId and r.startTime >= :from and r.status in :statuses
           order by r.startTime
           """)
    List<Reservation> findUpcoming(@Param("businessId") Long businessId,
                                   @Param("from") Instant from,
                                   @Param("statuses") Collection<ReservationStatus> statuses,
                                   Pageable pageable);

    /** One customer's reservations that are still ahead, soonest first. */
    @EntityGraph(attributePaths = "resources")
    @Query("""
           select r from Reservation r
           where r.businessId = :businessId and r.customerId = :customerId
             and r.startTime >= :from and r.status in :statuses
           order by r.startTime
           """)
    List<Reservation> findUpcomingForCustomer(@Param("businessId") Long businessId,
                                              @Param("customerId") Long customerId,
                                              @Param("from") Instant from,
                                              @Param("statuses") Collection<ReservationStatus> statuses);
}
