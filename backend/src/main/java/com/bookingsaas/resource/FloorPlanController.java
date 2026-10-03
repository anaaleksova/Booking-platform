package com.bookingsaas.resource;

import com.bookingsaas.resource.dto.FloorPlanDtos.FloorPlanResponse;
import com.bookingsaas.resource.dto.FloorPlanDtos.FloorPlansRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/resource-types/{resourceTypeId}/floor-plans")
@Tag(name = "Admin · Resources")
public class FloorPlanController {

    private final FloorPlanService floorPlanService;

    public FloorPlanController(FloorPlanService floorPlanService) {
        this.floorPlanService = floorPlanService;
    }

    /** The type's floors in order; empty when none has been drawn. */
    @GetMapping
    public List<FloorPlanResponse> list(@PathVariable Long resourceTypeId) {
        return floorPlanService.list(resourceTypeId);
    }

    @PutMapping
    @Operation(summary = "Save every floor at once: names, order, room and placed resources. "
            + "Floors left out are deleted; an empty list removes the floor plans.")
    public List<FloorPlanResponse> saveAll(@PathVariable Long resourceTypeId,
                                           @Valid @RequestBody FloorPlansRequest request) {
        return floorPlanService.saveAll(resourceTypeId, request);
    }
}
