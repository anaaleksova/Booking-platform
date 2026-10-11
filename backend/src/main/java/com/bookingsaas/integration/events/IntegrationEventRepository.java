package com.bookingsaas.integration.events;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, Long> {

    @Query("""
           select e from IntegrationEvent e
           where e.status = com.bookingsaas.integration.events.IntegrationEvent.Status.PENDING
             and e.dueAt <= :now
           order by e.dueAt
           """)
    List<IntegrationEvent> findDue(@Param("now") Instant now, Pageable pageable);

    List<IntegrationEvent> findByReservationIdAndEventTypeAndStatus(Long reservationId, String eventType,
                                                                    IntegrationEvent.Status status);

    List<IntegrationEvent> findByBusinessIdOrderByCreatedAtDesc(Long businessId, Pageable pageable);
}
