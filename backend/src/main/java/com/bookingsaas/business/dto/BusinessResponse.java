package com.bookingsaas.business.dto;

import com.bookingsaas.business.Business;

import java.time.Instant;

/** The tenant's own view of its profile. */
public record BusinessResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String phone,
        String email,
        String address,
        String timezone,
        boolean active,
        Instant createdAt
) {

    public static BusinessResponse from(Business business) {
        return new BusinessResponse(business.getId(), business.getName(), business.getSlug(),
                business.getDescription(), business.getLogoUrl(), business.getPhone(), business.getEmail(),
                business.getAddress(), business.getTimezone(), business.isActive(), business.getCreatedAt());
    }
}
