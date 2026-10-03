package com.bookingsaas.business.dto;

import com.bookingsaas.business.GalleryImage;
import com.bookingsaas.media.Media;

import java.util.UUID;

public record GalleryImageResponse(Long id, UUID mediaId, String url, String caption, int position) {

    public static GalleryImageResponse from(GalleryImage image) {
        return new GalleryImageResponse(image.getId(), image.getMediaId(), Media.urlOf(image.getMediaId()),
                image.getCaption(), image.getPosition());
    }
}
