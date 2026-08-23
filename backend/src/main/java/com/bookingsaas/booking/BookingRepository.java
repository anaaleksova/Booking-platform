package com.bookingsaas.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Booking> findByPublicReference(UUID publicReference);

    List<Booking> findByBusinessIdAndCustomerIdOrderByStartDateTimeDesc(Long businessId, Long customerId);

    /**
     * Bookings that hold time in {@code [from, to)}.
     *
     * <p>Filtered on the block window, so buffers count as occupied. The
     * excluded id lets a booking being rescheduled ignore itself; callers pass
     * a sentinel rather than null to keep the parameter's type unambiguous.
     */
    @Query("""
           select b from Booking b
           where b.businessId = :businessId
             and b.status in :statuses
             and b.blockStartDateTime < :to
             and b.blockEndDateTime > :from
             and b.id <> :excludeBookingId
           order by b.blockStartDateTime
           """)
    List<Booking> findOccupying(@Param("businessId") Long businessId,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                @Param("statuses") Collection<BookingStatus> statuses,
                                @Param("excludeBookingId") Long excludeBookingId);

    /** Upcoming appointments, used by the dashboard summary. */
    long countByBusinessIdAndStatusAndStartDateTimeAfter(Long businessId, BookingStatus status, Instant after);

    long countByBusinessIdAndStatusAndStartDateTimeBetween(Long businessId, BookingStatus status,
                                                           Instant from, Instant to);

    long countByBusinessIdAndStartDateTimeBetween(Long businessId, Instant from, Instant to);

    long countByBusinessIdAndCustomerIdAndStatus(Long businessId, Long customerId, BookingStatus status);

    long countByBusinessIdAndCustomerId(Long businessId, Long customerId);

    /** Any currency actually charged, used to label a revenue figure that has no single source row. */
    Optional<Booking> findFirstByBusinessIdAndCurrencyIsNotNullOrderByStartDateTimeDesc(Long businessId);

    // --- analytics -----------------------------------------------------

    /** Total value of non-cancelled bookings starting in {@code [from, to)}. */
    @Query("""
           select coalesce(sum(b.price), 0) from Booking b
           where b.businessId = :businessId and b.status <> :excludedStatus
             and b.startDateTime >= :from and b.startDateTime < :to
           """)
    BigDecimal sumRevenue(@Param("businessId") Long businessId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          @Param("excludedStatus") BookingStatus excludedStatus);

    /** Appointment counts and revenue per status, for the reports overview. */
    @Query("""
           select b.status as status, count(b) as count, coalesce(sum(b.price), 0) as revenue
           from Booking b
           where b.businessId = :businessId and b.startDateTime >= :from and b.startDateTime < :to
           group by b.status
           """)
    List<StatusAggregateRow> aggregateByStatus(@Param("businessId") Long businessId,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to);

    /** Revenue and appointment volume per service, for the "what makes money" report. */
    @Query("""
           select b.serviceId as serviceId, count(b) as appointmentCount, coalesce(sum(b.price), 0) as revenue,
                  max(b.currency) as currency
           from Booking b
           where b.businessId = :businessId and b.status <> :excludedStatus
             and b.startDateTime >= :from and b.startDateTime < :to
           group by b.serviceId
           """)
    List<ServiceRevenueRow> aggregateRevenueByService(@Param("businessId") Long businessId,
                                                       @Param("from") Instant from,
                                                       @Param("to") Instant to,
                                                       @Param("excludedStatus") BookingStatus excludedStatus);

    /** Customers ranked by what they've spent; drives the customer analytics table. */
    @Query(value = """
           select b.customerId as customerId, count(b) as appointmentCount,
                  coalesce(sum(b.price), 0) as totalSpent, max(b.startDateTime) as lastAppointmentAt
           from Booking b
           where b.businessId = :businessId and b.status <> :excludedStatus
             and b.startDateTime >= :from and b.startDateTime < :to
           group by b.customerId
           order by sum(b.price) desc
           """,
           countQuery = """
           select count(distinct b.customerId) from Booking b
           where b.businessId = :businessId and b.status <> :excludedStatus
             and b.startDateTime >= :from and b.startDateTime < :to
           """)
    Page<CustomerAggregateRow> aggregateCustomerSpend(@Param("businessId") Long businessId,
                                                      @Param("from") Instant from,
                                                      @Param("to") Instant to,
                                                      @Param("excludedStatus") BookingStatus excludedStatus,
                                                      Pageable pageable);

    /** Cancellations for a specific set of customers, scoped to the current page. */
    @Query("""
           select b.customerId as customerId, count(b) as cancellationCount
           from Booking b
           where b.businessId = :businessId and b.status = :cancelledStatus
             and b.customerId in :customerIds
             and b.startDateTime >= :from and b.startDateTime < :to
           group by b.customerId
           """)
    List<CustomerCancellationRow> countCancellationsForCustomers(@Param("businessId") Long businessId,
                                                                  @Param("customerIds") Collection<Long> customerIds,
                                                                  @Param("from") Instant from,
                                                                  @Param("to") Instant to,
                                                                  @Param("cancelledStatus") BookingStatus cancelledStatus);

    /** Per-service booking counts for a set of customers, reduced to a favourite service. */
    @Query("""
           select b.customerId as customerId, b.serviceId as serviceId, count(b) as count
           from Booking b
           where b.businessId = :businessId and b.status <> :excludedStatus
             and b.customerId in :customerIds
             and b.startDateTime >= :from and b.startDateTime < :to
           group by b.customerId, b.serviceId
           """)
    List<CustomerServiceCountRow> aggregateServiceCountsForCustomers(@Param("businessId") Long businessId,
                                                                      @Param("customerIds") Collection<Long> customerIds,
                                                                      @Param("from") Instant from,
                                                                      @Param("to") Instant to,
                                                                      @Param("excludedStatus") BookingStatus excludedStatus);
}
