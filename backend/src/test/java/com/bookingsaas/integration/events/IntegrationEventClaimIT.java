package com.bookingsaas.integration.events;

import com.bookingsaas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Webhook events, like notifications, are claimed by one worker at a time. */
class IntegrationEventClaimIT extends AbstractIntegrationTest {

    @Autowired
    private IntegrationEventDispatcher dispatcher;

    @Test
    void concurrent_claims_never_overlap_and_claimed_events_wait_for_their_lease() throws Exception {
        registerAndLogin("owner@events.test", "Events Co");
        long businessId = jdbcTemplate.queryForObject("select id from business", Long.class);
        for (int i = 0; i < 40; i++) {
            jdbcTemplate.update("""
                    insert into integration_event (business_id, event_type, due_at)
                    values (?, 'reservation.created', now() - interval '1 minute')""", businessId);
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<List<IntegrationEvent>> first = pool.submit(() -> {
            start.await();
            return dispatcher.claimDue();
        });
        Future<List<IntegrationEvent>> second = pool.submit(() -> {
            start.await();
            return dispatcher.claimDue();
        });
        start.countDown();
        List<Long> a = first.get().stream().map(IntegrationEvent::getId).toList();
        List<Long> b = second.get().stream().map(IntegrationEvent::getId).toList();
        pool.shutdown();

        Set<Long> both = new HashSet<>(a);
        both.retainAll(b);
        assertThat(both).isEmpty();
        assertThat(a.size() + b.size()).isEqualTo(40);
        assertThat(dispatcher.claimDue()).isEmpty();
    }
}
