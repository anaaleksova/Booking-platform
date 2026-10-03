package com.bookingsaas.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
           select n from Notification n
           where n.status = com.bookingsaas.notification.NotificationStatus.PENDING
             and n.scheduledAt <= :now
           order by n.scheduledAt
           """)
    List<Notification> findDue(@Param("now") Instant now, Pageable pageable);

    boolean existsByReservationIdAndType(Long reservationId, NotificationType type);

    List<Notification> findByReservationIdOrderByCreatedAtDesc(Long reservationId);
}
