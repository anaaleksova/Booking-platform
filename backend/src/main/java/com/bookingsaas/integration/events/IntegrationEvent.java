package com.bookingsaas.integration.events;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One event waiting for, or done with, delivery to the automation layer.
 * The payload is built at delivery time from the current data, so a retried
 * event never carries stale details.
 */
@Entity
@Table(name = "integration_event")
@Getter
@Setter
@NoArgsConstructor
public class IntegrationEvent extends Auditable {

    static final int MAX_ATTEMPTS = 8;
    private static final Duration FIRST_RETRY = Duration.ofSeconds(30);
    private static final Duration LONGEST_RETRY = Duration.ofHours(1);

    public enum Status {
        PENDING,
        DELIVERED,
        FAILED,
        SKIPPED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "delivery_id", nullable = false, unique = true)
    private UUID deliveryId = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    public void markDelivered(Instant when, int httpStatus) {
        status = Status.DELIVERED;
        deliveredAt = when;
        responseStatus = httpStatus;
        attempts++;
        lastError = null;
    }

    /** Backs off exponentially; gives up after {@link #MAX_ATTEMPTS}. */
    public void markAttemptFailed(Instant now, Integer httpStatus, String error) {
        attempts++;
        responseStatus = httpStatus;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (attempts >= MAX_ATTEMPTS) {
            status = Status.FAILED;
            return;
        }
        Duration wait = FIRST_RETRY.multipliedBy(1L << Math.min(attempts - 1, 10));
        dueAt = now.plus(wait.compareTo(LONGEST_RETRY) > 0 ? LONGEST_RETRY : wait);
    }

    public void markSkipped(String reason) {
        status = Status.SKIPPED;
        lastError = reason;
    }
}
