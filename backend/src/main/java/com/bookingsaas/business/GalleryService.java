package com.bookingsaas.business;

import com.bookingsaas.business.dto.GalleryImageResponse;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.media.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The optional photo gallery on a business's booking page: add, caption,
 * reorder, remove. Capped so the page stays quick on a phone.
 */
@Service
public class GalleryService {

    public static final int MAX_IMAGES = 12;

    private final GalleryImageRepository repository;
    private final MediaService mediaService;
    private final CurrentTenant currentTenant;

    public GalleryService(GalleryImageRepository repository, MediaService mediaService, CurrentTenant currentTenant) {
        this.repository = repository;
        this.mediaService = mediaService;
        this.currentTenant = currentTenant;
    }

    /** The gallery as customers see it, in order. Empty when the business hides it. */
    @Transactional(readOnly = true)
    public List<GalleryImageResponse> publicGallery(Business business) {
        if (!business.isShowGallery()) {
            return List.of();
        }
        return repository.findByBusinessIdOrderByPositionAscIdAsc(business.getId()).stream()
                .map(GalleryImageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GalleryImageResponse> list() {
        return repository.findByBusinessIdOrderByPositionAscIdAsc(currentTenant.businessId()).stream()
                .map(GalleryImageResponse::from)
                .toList();
    }

    @Transactional
    public GalleryImageResponse add(UUID mediaId, String caption) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        long count = repository.countByBusinessId(businessId);
        if (count >= MAX_IMAGES) {
            throw ApiException.validation("The gallery can hold up to " + MAX_IMAGES + " photos.");
        }
        GalleryImage image = new GalleryImage();
        image.setBusinessId(businessId);
        image.setMediaId(mediaService.requireOwned(businessId, mediaId));
        image.setCaption(clean(caption));
        image.setPosition((int) count);
        return GalleryImageResponse.from(repository.save(image));
    }

    @Transactional
    public GalleryImageResponse updateCaption(Long id, String caption) {
        currentTenant.requireOwner();
        GalleryImage image = require(id);
        image.setCaption(clean(caption));
        return GalleryImageResponse.from(repository.save(image));
    }

    /** Takes the complete new order: every gallery photo's id, exactly once. */
    @Transactional
    public List<GalleryImageResponse> reorder(List<Long> ids) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Map<Long, GalleryImage> images = repository.findByBusinessIdOrderByPositionAscIdAsc(businessId).stream()
                .collect(Collectors.toMap(GalleryImage::getId, Function.identity()));
        if (ids == null || ids.size() != images.size() || !images.keySet().equals(new HashSet<>(ids))) {
            throw ApiException.validation("List every gallery photo exactly once.");
        }
        for (int position = 0; position < ids.size(); position++) {
            images.get(ids.get(position)).setPosition(position);
        }
        repository.saveAll(images.values());
        return list();
    }

    /** Removes the photo from the gallery and deletes the image itself. */
    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        GalleryImage image = require(id);
        repository.delete(image);
        repository.flush();
        mediaService.discardIfReplaced(businessId, image.getMediaId(), null);
    }

    private GalleryImage require(Long id) {
        return repository.findByIdAndBusinessId(id, currentTenant.businessId())
                .orElseThrow(() -> ApiException.notFound("Gallery photo", id));
    }

    private static String clean(String caption) {
        return caption == null || caption.isBlank() ? null : caption.trim();
    }
}
