package com.bookingsaas.media;

import java.util.Optional;

/**
 * Recognises the image formats the platform accepts from their first bytes.
 *
 * <p>The browser's declared content type is ignored on purpose: it is
 * whatever the client says. Sniffing the signature means a script renamed to
 * {@code photo.png} is refused, and what is served back is always an image.
 */
public enum ImageType {
    JPEG("image/jpeg"),
    PNG("image/png"),
    GIF("image/gif"),
    WEBP("image/webp");

    private final String contentType;

    ImageType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<ImageType> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        if (unsigned(bytes[0]) == 0xFF && unsigned(bytes[1]) == 0xD8 && unsigned(bytes[2]) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (unsigned(bytes[0]) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return Optional.of(PNG);
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return Optional.of(GIF);
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }
}
