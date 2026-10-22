package com.bookingsaas.notification;

import com.bookingsaas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Two workers draining the outbox at once never take the same message. */
class NotificationClaimIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationDispatcher dispatcher;

    private long businessId;

    @BeforeEach
    void business() throws Exception {
        registerAndLogin("owner@outbox.test", "Outbox Co");
        businessId = jdbcTemplate.queryForObject("select id from business", Long.class);
    }

    @Test
    void concurrent_claims_split_the_due_messages_between_them() throws Exception {
        queue(30, "now() - interval '1 minute'");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<List<Notification>> first = pool.submit(() -> {
            start.await();
            return dispatcher.claimDue();
        });
        Future<List<Notification>> second = pool.submit(() -> {
            start.await();
            return dispatcher.claimDue();
        });
        start.countDown();
        List<Long> a = first.get().stream().map(Notification::getId).toList();
        List<Long> b = second.get().stream().map(Notification::getId).toList();
        pool.shutdown();

        Set<Long> both = new HashSet<>(a);
        both.retainAll(b);
        assertThat(both).as("claimed by both workers").isEmpty();
        assertThat(a.size() + b.size()).isEqualTo(30);
    }

    @Test
    void a_claimed_message_is_not_claimed_again_until_its_lease_ends() {
        queue(3, "now() - interval '1 minute'");

        assertThat(dispatcher.claimDue()).hasSize(3);
        assertThat(dispatcher.claimDue()).isEmpty();

        // The lease pushed them into the future; a crashed worker's messages come back after it.
        Instant leasedUntil = jdbcTemplate.queryForObject("select min(scheduled_at) from notification", Instant.class);
        assertThat(leasedUntil).isAfter(Instant.now().plusSeconds(60));
    }

    @Test
    void messages_not_yet_due_or_already_sent_are_left_alone() {
        queue(2, "now() + interval '1 hour'");
        jdbcTemplate.update("""
                insert into notification (business_id, channel, type, recipient, body, status, scheduled_at)
                values (?, 'EMAIL', 'RESERVATION_CONFIRMED_CUSTOMER', 'a@example.com', 'x', 'SENT', now())""",
                businessId);

        assertThat(dispatcher.claimDue()).isEmpty();
    }

    @Test
    void a_failed_send_is_retried_later_and_then_given_up() {
        Notification notification = new Notification();
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        for (int attempt = 1; attempt < 5; attempt++) {
            notification.markAttemptFailed(now, "SMTP down");
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getScheduledAt()).isEqualTo(now.plusSeconds(60L * attempt));
        }
        notification.markAttemptFailed(now, "SMTP down");
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    private void queue(int count, String scheduledAt) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update("""
                    insert into notification (business_id, channel, type, recipient, body, scheduled_at)
                    values (?, 'EMAIL', 'RESERVATION_CONFIRMED_CUSTOMER', ?, 'body', %s)""".formatted(scheduledAt),
                    businessId, "customer" + i + "@example.com");
        }
    }
}
