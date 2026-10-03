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
import java.util.UUID;

/**
 * Something a customer can book.
 *
 * <p>Named {@code BookableService} rather than {@code Service} to keep the type
 * distinct from Spring's {@code @Service} stereotype in every file that touches
 * both.
 *
 * <p>A service never names concrete resource kinds. What it needs is data —
 * {@link ServiceResourceRequirement} rows such as "1 Treatment Room + 2
 * Employees" — which is what keeps the platform generic.
 */
@Entity
@Table(name = "service")
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

    /** Groups the price list on the booking page, e.g. "Massages". */
    @Column(length = 80)
    private String category;

    @Column(name = "image_media_id")
    private UUID imageMediaId;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** False: the price covers the whole reservation. True: it is charged per participant. */
    @Column(name = "price_per_participant", nullable = false)
    private boolean pricePerParticipant;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants = 1;

    /** Preparation time held before the appointment, e.g. setting up a room. */
    @Column(name = "buffer_before_minutes", nullable = false)
    private int bufferBeforeMinutes;

    /** Clean-up time held after the appointment; blocks the next reservation. */
    @Column(name = "buffer_after_minutes", nullable = false)
    private int bufferAfterMinutes;

    /** Master switch: without it, no requirement is customer-choosable. */
    @Column(name = "allow_customer_resource_selection", nullable = false)
    private boolean allowCustomerResourceSelection;

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
