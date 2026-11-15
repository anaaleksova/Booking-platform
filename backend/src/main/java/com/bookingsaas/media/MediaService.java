package com.bookingsaas.media;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
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

    /** Whether {@link #jpegOf} can serve this image: WebP needs a decoder Java does not ship. */
    public static boolean convertibleToJpeg(String contentType) {
        return !ImageType.WEBP.contentType().equals(contentType);
    }

    /**
     * The image as a JPEG — what Instagram insists on. PNG and GIF (its first
     * frame) are flattened onto white, since JPEG has no transparency.
     */
    @Transactional(readOnly = true)
    public byte[] jpegOf(UUID id) {
        Media media = require(id);
        if (ImageType.JPEG.contentType().equals(media.getContentType())) {
            return media.getData();
        }
        if (!convertibleToJpeg(media.getContentType())) {
            throw ApiException.validation("WebP images cannot be converted to JPEG.");
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(media.getData()));
            if (source == null) {
                throw ApiException.validation("The image could not be read.");
            }
            BufferedImage flat = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = flat.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, flat.getWidth(), flat.getHeight());
            graphics.drawImage(source, 0, 0, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(flat, "jpg", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw ApiException.validation("The image could not be converted to JPEG.");
        }
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

    /** Which of these images belong to the business; the rest are another business's, or gone. */
    @Transactional(readOnly = true)
    public Set<UUID> ownedAmong(Long businessId, Collection<UUID> mediaIds) {
        return mediaIds.isEmpty() ? Set.of() : Set.copyOf(repository.findOwnedIds(businessId, mediaIds));
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
