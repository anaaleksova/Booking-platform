package com.bookingsaas.resource.dto;

import com.bookingsaas.resource.FloorPlan;
import com.bookingsaas.resource.FloorPlanDecor;
import com.bookingsaas.resource.FloorPlanItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Request and response shapes of the floor-plan API. */
public final class FloorPlanDtos {

    private FloorPlanDtos() {
    }

    /**
     * Every floor of a resource type, in order. Saved as a whole: floors left
     * out are deleted, floors without an id are created.
     */
    public record FloorPlansRequest(@NotNull @Size(max = 20) List<@Valid @NotNull FloorPlanRequest> plans) {
    }

    /**
     * One floor. {@code id} is null for a new floor; {@code decor} may be omitted.
     */
    public record FloorPlanRequest(
            Long id,
            @NotBlank @Size(max = 60) String name,
            @NotNull @DecimalMin("0.25") @DecimalMax("4") BigDecimal aspectRatio,
            @NotNull @Size(max = 200) List<@Valid @NotNull Item> items,
            @Size(max = 100) List<@Valid @NotNull Decor> decor
    ) {

        public List<Decor> decorOrEmpty() {
            return decor == null ? List.of() : decor;
        }
    }

    /**
     * @param x      centre, % of the plan's width
     * @param y      centre, % of the plan's height
     * @param width  % of the plan's width
     * @param height % of the plan's height
     */
    public record Item(
            @NotNull Long resourceId,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal x,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal y,
            @NotNull @DecimalMin("2") @DecimalMax("60") BigDecimal width,
            @NotNull @DecimalMin("2") @DecimalMax("60") BigDecimal height,
            @NotNull FloorPlanItem.Shape shape
    ) {

        public static Item from(FloorPlanItem item) {
            return new Item(item.getResourceId(), item.getX(), item.getY(), item.getWidth(), item.getHeight(),
                    item.getShape());
        }
    }

    /** A non-bookable part of the room; centre and size in % like {@link Item}. */
    public record Decor(
            @NotNull FloorPlanDecor.Kind kind,
            @Size(max = 40) String label,
            FloorPlanDecor.Tone tone,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal x,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal y,
            @NotNull @DecimalMin("0.5") @DecimalMax("100") BigDecimal width,
            @NotNull @DecimalMin("0.5") @DecimalMax("100") BigDecimal height
    ) {

        public static Decor from(FloorPlanDecor decor) {
            return new Decor(decor.getKind(), decor.getLabel(), decor.getTone(), decor.getX(), decor.getY(),
                    decor.getWidth(), decor.getHeight());
        }
    }

    public record FloorPlanResponse(
            Long id,
            Long resourceTypeId,
            String name,
            BigDecimal aspectRatio,
            List<Item> items,
            List<Decor> decor
    ) {

        public static FloorPlanResponse from(FloorPlan plan) {
            return new FloorPlanResponse(plan.getId(), plan.getResourceTypeId(), plan.getName(), plan.getAspectRatio(),
                    plan.getItems().stream().map(Item::from).toList(),
                    plan.getDecor().stream().map(Decor::from).toList());
        }
    }
}
