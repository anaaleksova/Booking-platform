package com.bookingsaas.service;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Something a customer can book.
 *
 * <p>Named {@code BookableService} rather than {@code Service} to keep the type
 * distinct from Spring's {@code @Service} stereotype in every file that touches
 * both.
 *
 * <p>{@code requiresStaff} / {@code requiresResource} are what make the platform
 * generic: a haircut needs a person, a meeting room needs a room, a reformer
 * class needs both — expressed as data, never as a business-type branch.
 */
@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
public class BookableService extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    /** Preparation time held before the appointment, e.g. setting up a room. */
    @Column(name = "buffer_before_minutes", nullable = false)
    private int bufferBeforeMinutes;

    /** Clean-up time held after the appointment; blocks the next booking. */
    @Column(name = "buffer_after_minutes", nullable = false)
    private int bufferAfterMinutes;

    @Column(name = "requires_staff", nullable = false)
    private boolean requiresStaff = true;

    @Column(name = "requires_resource", nullable = false)
    private boolean requiresResource;

    @Column(nullable = false)
    private boolean active = true;

    public Duration duration() {
        return Duration.ofMinutes(durationMinutes);
    }

    public Duration bufferBefore() {
        return Duration.ofMinutes(bufferBeforeMinutes);
    }

    public Duration bufferAfter() {
        return Duration.ofMinutes(bufferAfterMinutes);
    }
}
