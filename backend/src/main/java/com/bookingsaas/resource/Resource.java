package com.bookingsaas.resource;

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

import java.util.UUID;

/**
 * A concrete reservable item of one {@link ResourceType}: Room 2, Table 5,
 * Machine 3 — or, for the employee type, a person.
 *
 * <p>Resources are what let one engine serve a spa, a restaurant and a car
 * wash without any per-industry code.
 */
@Entity
@Table(name = "resource")
@Getter
@Setter
@NoArgsConstructor
public class Resource extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "resource_type_id", nullable = false)
    private Long resourceTypeId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * How many participants the resource serves at once — a table's seats, a
     * room's occupancy. {@code null} means participants do not limit it.
     */
    @Column
    private Integer capacity;

    /**
     * Several bookings may use it at once until its seats are taken: a party
     * of two at a shared eight-seat table leaves six seats for others.
     * Requires a capacity.
     */
    @Column(name = "shared_seating", nullable = false)
    private boolean sharedSeating;

    /** The smallest group a private resource is given to; {@code null} for any group. */
    @Column(name = "min_party_size")
    private Integer minPartySize;

    /** A photo; for an employee's resource this is the employee's picture. */
    @Column(name = "image_media_id")
    private UUID imageMediaId;

    @Column(nullable = false)
    private boolean active = true;
}
