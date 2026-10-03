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
 * One resource's spot on a floor plan. Position (the centre) and size are
 * percentages of the plan, so the map scales to any screen.
 */
@Entity
@Table(name = "floor_plan_item")
@Getter
@Setter
@NoArgsConstructor
public class FloorPlanItem {

    public enum Shape { ROUND, SQUARE, RECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal x;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal y;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal width;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal height;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Shape shape = Shape.ROUND;
}
