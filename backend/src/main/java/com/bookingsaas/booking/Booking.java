package com.bookingsaas.booking;

import com.bookingsaas.common.domain.Auditable;
import com.bookingsaas.common.domain.TimeRange;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An appointment.
 *
 * <p>Two windows are stored. {@code start/end} is what the customer booked and
 * what the calendar draws. {@code blockStart/blockEnd} adds the service buffers
 * and is what conflict detection uses — in the engine, in the service layer and
 * in the database exclusion constraints alike. Persisting the block window
 * means later edits to a service's buffers cannot retroactively make existing
 * bookings overlap.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "start_date_time", nullable = false)
    private Instant startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private Instant endDateTime;

    @Column(name = "block_start_date_time", nullable = false)
    private Instant blockStartDateTime;

    @Column(name = "block_end_date_time", nullable = false)
    private Instant blockEndDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "customer_notes", columnDefinition = "text")
    private String customerNotes;

    /** Never shown to the customer. */
    @Column(name = "internal_notes", columnDefinition = "text")
    private String internalNotes;

    /** Price as charged at booking time; later price changes do not rewrite history. */
    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingSource source = BookingSource.PUBLIC;

    /** Unguessable handle for the customer's own confirmation and cancel link. */
    @Column(name = "public_reference", nullable = false, unique = true)
    private UUID publicReference = UUID.randomUUID();

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    public TimeRange appointmentRange() {
        return TimeRange.of(startDateTime, endDateTime);
    }

    public TimeRange blockRange() {
        return TimeRange.of(blockStartDateTime, blockEndDateTime);
    }
}
