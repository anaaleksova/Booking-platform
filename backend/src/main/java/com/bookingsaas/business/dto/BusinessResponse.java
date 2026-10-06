package com.bookingsaas.business.dto;

import com.bookingsaas.business.Business;
import com.bookingsaas.media.Media;

import java.time.Instant;
import java.util.UUID;

/** The tenant's own view of its profile. */
public record BusinessResponse(
        Long id,
        String name,
        String slug,
        String description,
        String tagline,
        String accentColor,
        UUID logoMediaId,
        String logoUrl,
        UUID coverMediaId,
        String coverUrl,
        String instagramUrl,
        String facebookUrl,
        String websiteUrl,
        boolean showMap,
        boolean showGallery,
        boolean darkTheme,
        String phone,
        String email,
        String address,
        String currency,
        String timezone,
        boolean active,
        Instant createdAt
) {

    public static BusinessResponse from(Business business) {
        return new BusinessResponse(business.getId(), business.getName(), business.getSlug(),
                business.getDescription(), business.getTagline(), business.getAccentColor(),
                business.getLogoMediaId(), Media.urlOf(business.getLogoMediaId()),
                business.getCoverMediaId(), Media.urlOf(business.getCoverMediaId()),
                business.getInstagramUrl(), business.getFacebookUrl(), business.getWebsiteUrl(),
                business.isShowMap(), business.isShowGallery(), business.isDarkTheme(), business.getPhone(), business.getEmail(),
                business.getAddress(), business.getCurrency(), business.getTimezone(), business.isActive(), business.getCreatedAt());
    }
}
