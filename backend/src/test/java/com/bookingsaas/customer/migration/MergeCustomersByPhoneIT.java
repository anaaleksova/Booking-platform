package com.bookingsaas.customer.migration;

import com.bookingsaas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Customers stored twice under differently written numbers become one. */
class MergeCustomersByPhoneIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    private long businessId;
    private long otherBusinessId;

    @BeforeEach
    void businesses() throws Exception {
        registerAndLogin("owner@one.test", "Business One");
        registerAndLogin("owner@two.test", "Business Two");
        List<Long> ids = jdbcTemplate.queryForList("select id from business order by id", Long.class);
        businessId = ids.get(0);
        otherBusinessId = ids.get(1);
        jdbcTemplate.update("update business set phone = '+389 2 312 4567'");
    }

    @Test
    void customers_sharing_a_number_are_merged_into_the_oldest() throws Exception {
        long fromChat = customer(businessId, "Ana", "", null, "+38970555444", null);
        long fromWeb = customer(businessId, "Ana", "Petrova", "ana@example.com", "+389 70 555 444", "Prefers mornings");
        long fromAdmin = customer(businessId, "Ana", "P.", "ana.work@example.com", "070 555 444", null);
        long someoneElse = customer(businessId, "Bojan", "", "bojan@example.com", "+389 71 000 111", null);
        long otherBusiness = customer(otherBusinessId, "Ana", "", null, "070 555 444", null);
        reservationFor(fromWeb);
        reservationFor(fromAdmin);
        jdbcTemplate.update("""
                insert into customer_external_identity (business_id, customer_id, provider, external_user_id)
                values (?, ?, 'INSTAGRAM', 'ig-ana')""", businessId, fromAdmin);

        MergeCustomersByPhone.Result result;
        try (Connection connection = dataSource.getConnection()) {
            result = MergeCustomersByPhone.run(connection);
        }

        assertThat(result.merged()).isEqualTo(2);
        Map<String, Object> ana = jdbcTemplate.queryForMap("select * from customer where id = ?", fromChat);
        assertThat(ana).containsEntry("phone", "+38970555444")
                .containsEntry("email", "ana@example.com")
                .containsEntry("last_name", "Petrova");
        assertThat((String) ana.get("notes")).contains("Prefers mornings", "Also used the email ana.work@example.com.");
        // Both visits and the Instagram link now belong to her one record.
        assertThat(jdbcTemplate.queryForObject("select count(*) from reservation where customer_id = ?", Long.class, fromChat))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select customer_id from customer_external_identity", Long.class))
                .isEqualTo(fromChat);
        assertThat(jdbcTemplate.queryForList("select id from customer where id in (?, ?)", Long.class, fromWeb, fromAdmin))
                .isEmpty();
        // Other people, and other businesses, are only reformatted.
        assertThat(jdbcTemplate.queryForObject("select phone from customer where id = ?", String.class, someoneElse))
                .isEqualTo("+38971000111");
        assertThat(jdbcTemplate.queryForObject("select count(*) from customer where id = ?", Long.class, otherBusiness))
                .isEqualTo(1);
    }

    @Test
    void running_it_again_changes_nothing() throws Exception {
        customer(businessId, "Ana", "", null, "+38970555444", null);
        customer(businessId, "Ana", "", "ana@example.com", "070 555 444", null);
        try (Connection connection = dataSource.getConnection()) {
            MergeCustomersByPhone.run(connection);
            assertThat(MergeCustomersByPhone.run(connection)).isEqualTo(new MergeCustomersByPhone.Result(0, 0));
        }
    }

    private long customer(long business, String first, String last, String email, String phone, String notes) {
        return jdbcTemplate.queryForObject("""
                insert into customer (business_id, first_name, last_name, email, phone, notes)
                values (?, ?, ?, ?, ?, ?) returning id""", Long.class, business, first, last, email, phone, notes);
    }

    private void reservationFor(long customerId) {
        long service = jdbcTemplate.queryForObject("""
                insert into service (business_id, name, duration_minutes, price)
                values (?, 'Visit ' || ?, 60, 10) returning id""", Long.class, businessId, customerId);
        jdbcTemplate.update("""
                insert into reservation (business_id, customer_id, service_id, start_time, end_time,
                                         block_start_time, block_end_time, status, number_of_participants,
                                         original_price, discount_amount, final_price, currency)
                values (?, ?, ?, now(), now() + interval '1 hour', now(), now() + interval '1 hour',
                        'COMPLETED', 1, 10, 0, 10, 'EUR')""", businessId, customerId, service);
    }
}
