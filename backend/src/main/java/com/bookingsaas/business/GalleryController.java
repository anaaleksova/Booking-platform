package com.bookingsaas.business;

import com.bookingsaas.business.dto.GalleryImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/gallery")
@Tag(name = "Admin · Business")
public class GalleryController {

    private final GalleryService galleryService;

    public GalleryController(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    public record AddRequest(@NotNull UUID mediaId, @Size(max = 200) String caption) {
    }

    public record CaptionRequest(@Size(max = 200) String caption) {
    }

    @GetMapping
    public List<GalleryImageResponse> list() {
        return galleryService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an uploaded image to the gallery (max 12)")
    public GalleryImageResponse add(@Valid @RequestBody AddRequest request) {
        return galleryService.add(request.mediaId(), request.caption());
    }

    @PutMapping("/{id}")
    public GalleryImageResponse updateCaption(@PathVariable Long id, @Valid @RequestBody CaptionRequest request) {
        return galleryService.updateCaption(id, request.caption());
    }

    @PutMapping("/order")
    @Operation(summary = "Set the display order: every gallery photo id, exactly once")
    public List<GalleryImageResponse> reorder(@RequestBody List<Long> ids) {
        return galleryService.reorder(ids);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        galleryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
