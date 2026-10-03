package com.bookingsaas.media;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores and serves images, and lets other modules attach them.
 *
 * <p>Attaching goes through {@link #requireOwned}: a business can only point
 * its logo or a service at an image it uploaded itself.
 */
@Service
public class MediaService {

    private final MediaRepository repository;
    private final CurrentTenant currentTenant;

    public MediaService(MediaRepository repository, CurrentTenant currentTenant) {
        this.repository = repository;
        this.currentTenant = currentTenant;
    }

    @Transactional
    public Media upload(MultipartFile file) {
        currentTenant.requireOwner();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.validation("The file could not be read.");
        }
        if (bytes.length == 0) {
            throw ApiException.validation("The file is empty.");
        }
        if (bytes.length > Media.MAX_BYTES) {
            throw ApiException.validation("Images can be at most 5 MB.");
        }
        ImageType type = ImageType.detect(bytes)
                .orElseThrow(() -> ApiException.validation("Upload a JPEG, PNG, WebP or GIF image."));

        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setBusinessId(currentTenant.businessId());
        media.setContentType(type.contentType());
        media.setSizeBytes(bytes.length);
        media.setData(bytes);
        return repository.save(media);
    }

    /** Public read: images are shown on the customer pages, so no tenant check applies. */
    @Transactional(readOnly = true)
    public Media require(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Image", id));
    }

    /**
     * Validates that an image about to be attached belongs to the business.
     * {@code null} means "no image" and is always allowed.
     */
    @Transactional(readOnly = true)
    public UUID requireOwned(Long businessId, UUID mediaId) {
        if (mediaId == null) {
            return null;
        }
        return repository.findByIdAndBusinessId(mediaId, businessId)
                .map(Media::getId)
                .orElseThrow(() -> ApiException.validation("The selected image does not exist."));
    }

    /**
     * Deletes an image that was replaced or removed. Anything else still
     * pointing at it is released by the {@code ON DELETE SET NULL} keys.
     */
    @Transactional
    public void discardIfReplaced(Long businessId, UUID previous, UUID current) {
        if (previous != null && !Objects.equals(previous, current)) {
            repository.findByIdAndBusinessId(previous, businessId).ifPresent(repository::delete);
        }
    }
}
