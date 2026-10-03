package com.bookingsaas.resource;

import com.bookingsaas.resource.dto.ResourceTypeRequest;
import com.bookingsaas.resource.dto.ResourceTypeResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/admin/resource-types")
@Tag(name = "Admin · Resources")
public class ResourceTypeController {

    private final ResourceTypeService resourceTypeService;

    public ResourceTypeController(ResourceTypeService resourceTypeService) {
        this.resourceTypeService = resourceTypeService;
    }

    @GetMapping
    public List<ResourceTypeResponse> list() {
        return resourceTypeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceTypeResponse create(@Valid @RequestBody ResourceTypeRequest request) {
        return resourceTypeService.create(request);
    }

    @PutMapping("/{id}")
    public ResourceTypeResponse update(@PathVariable Long id, @Valid @RequestBody ResourceTypeRequest request) {
        return resourceTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        resourceTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
