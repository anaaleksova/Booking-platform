package com.bookingsaas.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded image — a logo, a cover photo, a service or employee photo.
 *
 * <p>The id is a random UUID, so a public URL cannot be guessed from another
 * one; the bytes never change under an id, which is what lets the public
 * endpoint cache them forever.
 */
@Entity
@Table(name = "media")
@Getter
@Setter
@NoArgsConstructor
public class Media {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    @Id
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** The public address of the image, relative to the API origin. */
    public static String urlOf(UUID id) {
        return id == null ? null : "/api/public/media/" + id;
    }
}
