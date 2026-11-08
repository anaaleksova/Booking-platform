package com.bookingsaas.publicapi.dto;

import com.bookingsaas.business.Business;
import com.bookingsaas.business.dto.GalleryImageResponse;
import com.bookingsaas.business.dto.SpecialHours;
import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.media.Media;
import com.bookingsaas.site.model.SiteDocument;

import java.util.List;

/**
 * What a visitor may see about a business: the shop window, as the business
 * configured it. Nothing operational — no settings, no counts, no ids.
 */
public record PublicBusinessResponse(
        String name,
        String slug,
        String tagline,
        String description,
        String accentColor,
        String logoUrl,
        String coverUrl,
        String phone,
        String email,
        String address,
        String instagramUrl,
        String facebookUrl,
        String websiteUrl,
        boolean showMap,
        boolean darkTheme,
        String timezone,
        String currency,
        List<DayInterval> openingHours,
        /* Dates in the coming weeks whose hours differ from the week's. */
        List<SpecialHours> specialHours,
        /* Empty when the business has no photos or hides the gallery. */
        List<GalleryPhoto> gallery,
        /* The published homepage theme: every customer page of the business is drawn in it. */
        SiteDocument.Theme theme
) {

    public record GalleryPhoto(String url, String caption) {
    }

    public static PublicBusinessResponse from(Business business, List<DayInterval> openingHours,
                                              List<SpecialHours> specialHours,
                                              List<GalleryImageResponse> gallery, SiteDocument.Theme theme) {
        return new PublicBusinessResponse(business.getName(), business.getSlug(), business.getTagline(),
                business.getDescription(), business.getAccentColor(), Media.urlOf(business.getLogoMediaId()),
                Media.urlOf(business.getCoverMediaId()), business.getPhone(), business.getEmail(),
                business.getAddress(), business.getInstagramUrl(), business.getFacebookUrl(),
                business.getWebsiteUrl(), business.isShowMap(), business.isDarkTheme(), business.getTimezone(), business.getCurrency(),
                openingHours, specialHours, gallery.stream().map(image -> new GalleryPhoto(image.url(), image.caption())).toList(),
                theme);
    }
}
