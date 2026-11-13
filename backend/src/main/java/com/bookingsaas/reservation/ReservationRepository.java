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

    /** Bookings not yet over that hold the resource at some moment of {@code [from, to)}, buffers included. */
    @EntityGraph(attributePaths = "resources")
    @Query("""
           select distinct r from Reservation r join r.resources held
           where r.businessId = :businessId and r.status in :statuses and r.endTime > :now
             and held.resourceId = :resourceId and held.occupying = true
             and held.blockStartTime < :to and held.blockEndTime > :from
           order by r.startTime
           """)
    List<Reservation> findHoldingResource(@Param("businessId") Long businessId,
                                          @Param("resourceId") Long resourceId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to,
                                          @Param("now") Instant now,
                                          @Param("statuses") Collection<ReservationStatus> statuses);

    /**
     * Per inactive resource, how many bookings not yet over still hold it:
     * bookings left on something that is no longer scheduled.
     */
    @Query("""
           select new com.bookingsaas.reservation.StrandedBookings(held.resourceId, count(distinct r.id))
           from Reservation r join r.resources held, Resource res
           where res.id = held.resourceId and res.active = false
             and r.businessId = :businessId and r.status in :statuses and r.endTime > :now
             and held.occupying = true
           group by held.resourceId
           """)
    List<StrandedBookings> countStranded(@Param("businessId") Long businessId,
                                         @Param("now") Instant now,
                                         @Param("statuses") Collection<ReservationStatus> statuses);

    /** Bookings not yet over whose appointment touches {@code [from, to)}. */
    @EntityGraph(attributePaths = "resources")
    @Query("""
           select r from Reservation r
           where r.businessId = :businessId and r.status in :statuses and r.endTime > :now
             and r.startTime < :to and r.endTime > :from
           order by r.startTime
           """)
    List<Reservation> findDuring(@Param("businessId") Long businessId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("now") Instant now,
                                 @Param("statuses") Collection<ReservationStatus> statuses);

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
