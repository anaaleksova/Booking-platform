package com.bookingsaas.reservation;

import com.bookingsaas.customer.spi.CustomerActivity;
import com.bookingsaas.customer.spi.CustomerActivityProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Reservation counts and spend per customer, for the customer list. One query per page. */
@Component
public class ReservationCustomerActivityProvider implements CustomerActivityProvider {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ReservationCustomerActivityProvider(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CustomerActivity> activityOf(Long businessId, Collection<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("businessId", businessId)
                .addValue("customerIds", customerIds)
                .addValue("now", clock.instant().atOffset(ZoneOffset.UTC));

        Map<Long, CustomerActivity> result = new HashMap<>();
        jdbc.query("""
                select customer_id,
                       count(*) filter (where status <> 'CANCELLED')                          as reservations,
                       coalesce(sum(final_price) filter (where status in ('CONFIRMED', 'COMPLETED')), 0) as spent,
                       max(start_time) filter (where status <> 'CANCELLED' and start_time <= :now) as last_at
                from reservation
                where business_id = :businessId and customer_id in (:customerIds)
                group by customer_id
                """, params, row -> {
            Timestamp last = row.getTimestamp("last_at");
            result.put(row.getLong("customer_id"), new CustomerActivity(row.getLong("reservations"),
                    row.getBigDecimal("spent"), last == null ? null : last.toInstant()));
        });
        return result;
    }
}
