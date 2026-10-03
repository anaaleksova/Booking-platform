package com.bookingsaas.discount;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * A promo code such as {@code SUMMER20}.
 *
 * <p>{@code usageCount} is only ever changed by guarded {@code UPDATE}s in
 * {@link DiscountRepository}, never by loading and saving the entity, so the
 * usage limit holds when two customers redeem the last use at once.
 */
@Entity
@Table(name = "discount")
@Getter
@Setter
@NoArgsConstructor
public class Discount extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 160)
    private String name;

    /** Stored upper-case; matched case-insensitively. */
    @Column(nullable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private DiscountType type;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    /** First valid day (inclusive, business timezone); null = no start limit. */
    @Column(name = "starts_on")
    private LocalDate startsOn;

    /** Last valid day (inclusive, business timezone); null = never expires. */
    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "max_usage")
    private Integer maxUsage;

    @Column(name = "usage_count", nullable = false, insertable = false, updatable = false)
    private int usageCount;

    @Column(name = "min_booking_value", precision = 12, scale = 2)
    private BigDecimal minBookingValue;

    @Column(name = "applies_to_all_services", nullable = false)
    private boolean appliesToAllServices = true;

    /** Eligible services when {@link #appliesToAllServices} is false. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discount_service", joinColumns = @JoinColumn(name = "discount_id"))
    @Column(name = "service_id")
    private Set<Long> serviceIds = new HashSet<>();
}
