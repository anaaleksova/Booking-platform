package com.bookingsaas.resource.dto;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.media.Media;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceType;

import java.util.List;
import java.util.UUID;

public record ResourceResponse(
        Long id,
        Long resourceTypeId,
        String resourceTypeName,
        String name,
        String description,
        Integer capacity,
        boolean sharedSeating,
        Integer minPartySize,
        boolean active,
        UUID imageMediaId,
        String imageUrl,
        /* True when this resource represents an employee and is edited through the employee module. */
        boolean employeeManaged,
        List<Long> serviceIds,
        /* Own weekly schedule; empty = follows the business opening hours. */
        List<DayInterval> hours
) {

    public static ResourceResponse from(Resource resource, ResourceType type, List<Long> serviceIds,
                                        List<DayInterval> hours) {
        return new ResourceResponse(resource.getId(), resource.getResourceTypeId(),
                type == null ? null : type.getName(), resource.getName(), resource.getDescription(),
                resource.getCapacity(), resource.isSharedSeating(), resource.getMinPartySize(), resource.isActive(), resource.getImageMediaId(),
                Media.urlOf(resource.getImageMediaId()), type != null && type.isEmployeeType(),
                serviceIds, hours);
    }
}
