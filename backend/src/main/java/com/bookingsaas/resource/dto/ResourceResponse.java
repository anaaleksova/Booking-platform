package com.bookingsaas.resource.dto;

import com.bookingsaas.resource.Resource;

import java.util.List;

public record ResourceResponse(
        Long id,
        String name,
        String description,
        boolean active,
        List<Long> serviceIds
) {

    public static ResourceResponse from(Resource resource, List<Long> serviceIds) {
        return new ResourceResponse(resource.getId(), resource.getName(), resource.getDescription(),
                resource.isActive(), serviceIds);
    }
}
