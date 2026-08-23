package com.bookingsaas.service;

import com.bookingsaas.service.dto.ServiceRequest;
import com.bookingsaas.service.dto.ServiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceCatalogService catalog;

    public ServiceController(ServiceCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ServiceResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return catalog.list(activeOnly);
    }

    @GetMapping("/{id}")
    public ServiceResponse get(@PathVariable Long id) {
        return catalog.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse create(@Valid @RequestBody ServiceRequest request) {
        return catalog.create(request);
    }

    @PutMapping("/{id}")
    public ServiceResponse update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return catalog.update(id, request);
    }

    /** Soft deactivation — the safe alternative to deleting a service in use. */
    @PatchMapping("/{id}/active")
    public ServiceResponse setActive(@PathVariable Long id, @RequestParam boolean value) {
        return catalog.setActive(id, value);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        catalog.delete(id);
        return ResponseEntity.noContent().build();
    }
}
