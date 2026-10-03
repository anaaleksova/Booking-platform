package com.bookingsaas.resource;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A map of the place for one resource type — where each table, desk or court
 * is, drawn with the room around it — so customers can see which spots are
 * free and pick one visually.
 */
@Entity
@Table(name = "floor_plan")
@Getter
@Setter
@NoArgsConstructor
public class FloorPlan extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "resource_type_id", nullable = false)
    private Long resourceTypeId;

    /** "Ground floor", "Terrace" — a type may have several plans, one per floor or area. */
    @Column(nullable = false, length = 60)
    private String name;

    /** Order among the type's plans, first shown first. */
    @Column(nullable = false)
    private int position;

    /** Width divided by height. */
    @Column(name = "aspect_ratio", nullable = false, precision = 5, scale = 3)
    private BigDecimal aspectRatio = new BigDecimal("1.6");

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "floor_plan_id", nullable = false)
    @OrderBy("id")
    private List<FloorPlanItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "floor_plan_id", nullable = false)
    @OrderBy("id")
    private List<FloorPlanDecor> decor = new ArrayList<>();
}
