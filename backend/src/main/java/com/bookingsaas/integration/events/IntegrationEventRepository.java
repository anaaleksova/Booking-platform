package com.bookingsaas.integration.events;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, Long> {

    /** Locks up to {@code limit} due events; {@code SKIP LOCKED} keeps two instances off the same rows. */
    @Query(value = """
           select id from integration_event
           where status = 'PENDING' and due_at <= :now
           order by due_at
           limit :limit
           for update skip locked
           """, nativeQuery = true)
    List<Long> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    /** Pushes the events out of reach of other workers until {@code until}: the claim. */
    @Modifying
    @Query("update IntegrationEvent e set e.dueAt = :until where e.id in :ids")
    int lease(@Param("ids") List<Long> ids, @Param("until") Instant until);

    List<IntegrationEvent> findByReservationIdAndEventTypeAndStatus(Long reservationId, String eventType,
                                                                    IntegrationEvent.Status status);

    List<IntegrationEvent> findByBusinessIdOrderByCreatedAtDesc(Long businessId, Pageable pageable);
}
