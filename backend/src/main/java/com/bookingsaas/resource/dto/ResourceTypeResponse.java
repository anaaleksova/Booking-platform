package com.bookingsaas.resource.dto;

import com.bookingsaas.resource.ResourceType;

public record ResourceTypeResponse(
        Long id,
        String name,
        String description,
        /* True for the one type whose resources are employees. */
        boolean employeeType,
        boolean active,
        long resourceCount,
        /* How many floor plans (floors or areas) the business has drawn for this type. */
        long floorPlanCount
) {

    public static ResourceTypeResponse from(ResourceType type, long resourceCount, long floorPlanCount) {
        return new ResourceTypeResponse(type.getId(), type.getName(), type.getDescription(),
                type.isEmployeeType(), type.isActive(), resourceCount, floorPlanCount);
    }
}
