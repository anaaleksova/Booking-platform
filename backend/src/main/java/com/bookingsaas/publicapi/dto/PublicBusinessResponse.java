package com.bookingsaas.publicapi.dto;

import com.bookingsaas.business.Business;

/**
 * What a stranger may see about a business.
 *
 * <p>Nothing operational is exposed — no ids of staff rows, no settings, no
 * counts. Just the shop window.
 */
public record PublicBusinessResponse(
        String name,
        String slug,
        String description,
        String logoUrl,
        String phone,
        String email,
        String address,
        String timezone
) {

    public static PublicBusinessResponse from(Business business) {
        return new PublicBusinessResponse(business.getName(), business.getSlug(), business.getDescription(),
                business.getLogoUrl(), business.getPhone(), business.getEmail(), business.getAddress(),
                business.getTimezone());
    }
}
