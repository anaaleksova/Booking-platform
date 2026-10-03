package com.bookingsaas.resource;

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

/**
 * A non-bookable part of the room drawn on a floor plan — an area, a wall,
 * the bar, a plant, the entrance — so customers recognise the place.
 * Position (the centre) and size are percentages of the plan.
 */
@Entity
@Table(name = "floor_plan_decor")
@Getter
@Setter
@NoArgsConstructor
public class FloorPlanDecor {

    public enum Kind { ZONE, WALL, COUNTER, PLANT, ENTRANCE }

    /** Floor tint of an area. */
    public enum Tone { GREEN, PINK, BLUE, SAND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Kind kind;

    @Column(length = 40)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Tone tone;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal x;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal y;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal width;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal height;
}
