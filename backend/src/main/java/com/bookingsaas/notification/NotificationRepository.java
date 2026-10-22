package com.bookingsaas.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Locks up to {@code limit} due messages for the current transaction.
     * {@code SKIP LOCKED}: a second instance draining at the same moment takes
     * other rows instead of the same ones, so nothing is sent twice.
     */
    @Query(value = """
           select id from notification
           where status = 'PENDING' and scheduled_at <= :now
           order by scheduled_at
           limit :limit
           for update skip locked
           """, nativeQuery = true)
    List<Long> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    /** Pushes the messages out of reach of other workers until {@code until}: the claim. */
    @Modifying
    @Query("update Notification n set n.scheduledAt = :until where n.id in :ids")
    int lease(@Param("ids") List<Long> ids, @Param("until") Instant until);

    boolean existsByReservationIdAndType(Long reservationId, NotificationType type);

    List<Notification> findByReservationIdOrderByCreatedAtDesc(Long reservationId);
}
