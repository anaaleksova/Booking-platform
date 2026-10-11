package com.bookingsaas.reservation;

import com.bookingsaas.availability.ResourceAllocation;
import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reservation: a customer, a service, a time, a group size, the concrete
 * resources assigned, and what it costs.
 *
 * <p>Two windows are stored. {@code start/end} is what the customer booked and
 * what the calendar draws. {@code blockStart/blockEnd} adds the service
 * buffers and is what conflict detection uses. Persisting the block window
 * means later edits to a service's buffers cannot make history overlap.
 *
 * <p>Status and window changes go through this class so the copies on
 * {@link ReservationResource} — the ones the exclusion constraint sees — can
 * never disagree with the reservation.
 */
@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
public class Reservation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Setter(lombok.AccessLevel.NONE)
    @Column(name = "block_start_time", nullable = false)
    private Instant blockStartTime;

    @Setter(lombok.AccessLevel.NONE)
    @Column(name = "block_end_time", nullable = false)
    private Instant blockEndTime;

    @Column(name = "number_of_participants", nullable = false)
    private int numberOfParticipants = 1;

    @Setter(lombok.AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    /** Price snapshot: later service or discount edits do not restate history. */
    @Column(name = "original_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "final_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "discount_id")
    private Long discountId;

    @Column(name = "discount_code", length = 40)
    private String discountCode;

    @Column(name = "customer_notes", columnDefinition = "text")
    private String customerNotes;

    /** Never shown to the customer. */
    @Column(name = "internal_notes", columnDefinition = "text")
    private String internalNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationSource source = ReservationSource.WEB;

    /** Unguessable handle for the customer's own reservation page. */
    @Column(name = "public_reference", nullable = false, unique = true)
    private UUID publicReference = UUID.randomUUID();

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationResource> resources = new ArrayList<>();

    /** Moves the reservation, keeping the assigned resources' windows in step. */
    public void schedule(ResourceAllocation allocation) {
        this.startTime = allocation.start();
        this.endTime = allocation.end();
        this.blockStartTime = allocation.blockStart();
        this.blockEndTime = allocation.blockEnd();
        resources.forEach(ReservationResource::sync);
    }

    /**
     * Replaces the assigned resources. Callers flush between clearing and
     * adding when the same resource may come back, because Hibernate inserts
     * before it deletes.
     */
    public void assignResources(List<ResourceAllocation.AllocatedResource> allocated) {
        resources.clear();
        allocated.forEach(item -> resources.add(new ReservationResource(this, item.resourceId(), item.requirementId(), item.seats())));
    }

    public void changeStatus(ReservationStatus target) {
        this.status = target;
        resources.forEach(ReservationResource::sync);
    }

    public List<Long> resourceIds() {
        return resources.stream().map(ReservationResource::getResourceId).toList();
    }
}
