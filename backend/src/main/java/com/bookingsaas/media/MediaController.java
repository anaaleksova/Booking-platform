package com.bookingsaas.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@RestController
@Tag(name = "Media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    public record UploadResponse(UUID id, String url, String contentType, int sizeBytes) {
    }

    @PostMapping(value = "/api/admin/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload an image (JPEG, PNG, WebP or GIF, max 5 MB); attach it by id afterwards")
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        Media media = mediaService.upload(file);
        return new UploadResponse(media.getId(), Media.urlOf(media.getId()), media.getContentType(),
                media.getSizeBytes());
    }

    /** The bytes behind an id never change, so browsers may cache them for a year. */
    @GetMapping("/api/public/media/{id}")
    public ResponseEntity<byte[]> serve(@PathVariable UUID id) {
        Media media = mediaService.require(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(media.getData());
    }
}
