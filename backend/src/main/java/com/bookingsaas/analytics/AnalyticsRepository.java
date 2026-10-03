package com.bookingsaas.analytics;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Aggregation queries over reservations, in plain SQL.
 *
 * <p>Every query is scoped by {@code business_id} and attributes a reservation
 * to the period of its <em>start time</em> (the day the service is delivered),
 * bucketed in the business timezone. Revenue is, explicitly:
 *
 * <pre>sum(final_price) where status in ('CONFIRMED', 'COMPLETED')</pre>
 *
 * <p>"Gross" is the same set summed over {@code original_price}; the
 * difference is the discounts given.
 */
@Repository
public class AnalyticsRepository {

    static final String REVENUE_STATUSES = "('CONFIRMED', 'COMPLETED')";

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RevenueRow(long reservations, BigDecimal gross, BigDecimal discounts, BigDecimal net) {
    }

    public record RevenuePeriodRow(LocalDate period, long reservations, BigDecimal gross, BigDecimal discounts,
                                   BigDecimal net) {
    }

    public record ReservationDayRow(LocalDate day, long pending, long confirmed, long completed, long cancelled,
                                    long participants) {
    }

    public record ServiceRow(long serviceId, long reservations, long participants, BigDecimal revenue) {
    }

    public record ResourceUsageRow(long resourceId, long reservations, double bookedMinutes) {
    }

    public record CustomerMixRow(long active, long newCustomers, long returning) {
    }

    public record TopCustomerRow(long customerId, long reservations, BigDecimal spent) {
    }

    public RevenueRow revenue(Long businessId, Instant from, Instant to) {
        return jdbc.queryForObject("""
                select count(*)                            as reservations,
                       coalesce(sum(original_price), 0)    as gross,
                       coalesce(sum(discount_amount), 0)   as discounts,
                       coalesce(sum(final_price), 0)       as net
                from reservation
                where business_id = :businessId
                  and status in """ + REVENUE_STATUSES + """
                  and start_time >= :from and start_time < :to
                """, window(businessId, from, to), (rs, i) -> new RevenueRow(rs.getLong("reservations"),
                rs.getBigDecimal("gross"), rs.getBigDecimal("discounts"), rs.getBigDecimal("net")));
    }

    /** @param unit {@code day}, {@code week} (ISO, Monday) or {@code month} */
    public List<RevenuePeriodRow> revenueByPeriod(Long businessId, Instant from, Instant to, String zone, String unit) {
        return jdbc.query("""
                select date_trunc(:unit, start_time at time zone :zone)::date as period,
                       count(*)                            as reservations,
                       coalesce(sum(original_price), 0)    as gross,
                       coalesce(sum(discount_amount), 0)   as discounts,
                       coalesce(sum(final_price), 0)       as net
                from reservation
                where business_id = :businessId
                  and status in """ + REVENUE_STATUSES + """
                  and start_time >= :from and start_time < :to
                group by 1
                order by 1
                """, window(businessId, from, to).addValue("zone", zone).addValue("unit", unit),
                (rs, i) -> new RevenuePeriodRow(rs.getObject("period", LocalDate.class), rs.getLong("reservations"),
                        rs.getBigDecimal("gross"), rs.getBigDecimal("discounts"), rs.getBigDecimal("net")));
    }

    public List<ReservationDayRow> reservationsByDay(Long businessId, Instant from, Instant to, String zone) {
        return jdbc.query("""
                select (start_time at time zone :zone)::date                        as day,
                       count(*) filter (where status = 'PENDING')                     as pending,
                       count(*) filter (where status = 'CONFIRMED')                   as confirmed,
                       count(*) filter (where status = 'COMPLETED')                   as completed,
                       count(*) filter (where status = 'CANCELLED')                   as cancelled,
                       coalesce(sum(number_of_participants) filter (where status <> 'CANCELLED'), 0) as participants
                from reservation
                where business_id = :businessId and start_time >= :from and start_time < :to
                group by 1
                order by 1
                """, window(businessId, from, to).addValue("zone", zone),
                (rs, i) -> new ReservationDayRow(rs.getObject("day", LocalDate.class), rs.getLong("pending"),
                        rs.getLong("confirmed"), rs.getLong("completed"), rs.getLong("cancelled"),
                        rs.getLong("participants")));
    }

    public List<ServiceRow> byService(Long businessId, Instant from, Instant to) {
        return jdbc.query("""
                select service_id,
                       count(*) filter (where status <> 'CANCELLED')                              as reservations,
                       coalesce(sum(number_of_participants) filter (where status <> 'CANCELLED'), 0) as participants,
                       coalesce(sum(final_price) filter (where status in """ + REVENUE_STATUSES + """
                       ), 0) as revenue
                from reservation
                where business_id = :businessId and start_time >= :from and start_time < :to
                group by service_id
                """, window(businessId, from, to),
                (rs, i) -> new ServiceRow(rs.getLong("service_id"), rs.getLong("reservations"),
                        rs.getLong("participants"), rs.getBigDecimal("revenue")));
    }

    /** Minutes each resource is held by non-cancelled reservations, clipped to the window. */
    public List<ResourceUsageRow> resourceUsage(Long businessId, Instant from, Instant to) {
        return jdbc.query("""
                select rr.resource_id,
                       count(distinct r.id) as reservations,
                       coalesce(sum(extract(epoch from (least(r.end_time, :to) - greatest(r.start_time, :from))) / 60), 0)
                           as booked_minutes
                from reservation_resource rr
                join reservation r on r.id = rr.reservation_id
                where rr.business_id = :businessId
                  and r.status <> 'CANCELLED'
                  and r.start_time < :to and r.end_time > :from
                group by rr.resource_id
                """, window(businessId, from, to),
                (rs, i) -> new ResourceUsageRow(rs.getLong("resource_id"), rs.getLong("reservations"),
                        rs.getDouble("booked_minutes")));
    }

    /**
     * Customers with a reservation in the window, split by whether their first
     * ever (non-cancelled) reservation also falls in it.
     */
    public CustomerMixRow customerMix(Long businessId, Instant from, Instant to) {
        return jdbc.queryForObject("""
                with firsts as (
                    select customer_id, min(start_time) as first_at
                    from reservation
                    where business_id = :businessId and status <> 'CANCELLED'
                    group by customer_id
                ), active as (
                    select distinct customer_id
                    from reservation
                    where business_id = :businessId and status <> 'CANCELLED'
                      and start_time >= :from and start_time < :to
                )
                select count(*)                                     as active,
                       count(*) filter (where f.first_at >= :from)  as new_customers,
                       count(*) filter (where f.first_at < :from)   as returning
                from active a join firsts f on f.customer_id = a.customer_id
                """, window(businessId, from, to),
                (rs, i) -> new CustomerMixRow(rs.getLong("active"), rs.getLong("new_customers"),
                        rs.getLong("returning")));
    }

    public List<TopCustomerRow> topCustomers(Long businessId, Instant from, Instant to, int limit) {
        return jdbc.query("""
                select customer_id, count(*) as reservations, coalesce(sum(final_price), 0) as spent
                from reservation
                where business_id = :businessId
                  and status in """ + REVENUE_STATUSES + """
                  and start_time >= :from and start_time < :to
                group by customer_id
                order by spent desc, reservations desc
                limit :limit
                """, window(businessId, from, to).addValue("limit", limit),
                (rs, i) -> new TopCustomerRow(rs.getLong("customer_id"), rs.getLong("reservations"),
                        rs.getBigDecimal("spent")));
    }

    public long countReservations(Long businessId, Instant from, Instant to) {
        Long count = jdbc.queryForObject("""
                select count(*) from reservation
                where business_id = :businessId and status <> 'CANCELLED'
                  and start_time >= :from and start_time < :to
                """, window(businessId, from, to), Long.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource window(Long businessId, Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("businessId", businessId)
                // OffsetDateTime binds as timestamptz: no dependence on the JVM zone.
                .addValue("from", from.atOffset(ZoneOffset.UTC))
                .addValue("to", to.atOffset(ZoneOffset.UTC));
    }
}
