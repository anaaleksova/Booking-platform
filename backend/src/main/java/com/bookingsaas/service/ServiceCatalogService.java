package com.bookingsaas.service;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.service.dto.ServiceRequest;
import com.bookingsaas.service.dto.ServiceResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for the bookable catalogue, always scoped to the caller's tenant. */
@Service
public class ServiceCatalogService {

    private final BookableServiceRepository repository;
    private final CurrentTenant currentTenant;

    public ServiceCatalogService(BookableServiceRepository repository, CurrentTenant currentTenant) {
        this.repository = repository;
        this.currentTenant = currentTenant;
    }

    // --- cross-module lookups ---------------------------------------------

    /** Loads a service for a known tenant. Used by booking and availability. */
    @Transactional(readOnly = true)
    public BookableService requireForBusiness(Long businessId, Long serviceId) {
        return repository.findByIdAndBusinessId(serviceId, businessId)
                .orElseThrow(() -> ApiException.notFound("Service", serviceId));
    }

    /** Loads a service that may actually be booked right now. */
    @Transactional(readOnly = true)
    public BookableService requireBookable(Long businessId, Long serviceId) {
        BookableService service = requireForBusiness(businessId, serviceId);
        if (!service.isActive()) {
            throw ApiException.of(ErrorCode.SERVICE_INACTIVE, "This service is no longer offered.");
        }
        return service;
    }

    @Transactional(readOnly = true)
    public List<BookableService> activeServicesOf(Long businessId) {
        return repository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId);
    }

    // --- dashboard API -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(boolean activeOnly) {
        Long businessId = currentTenant.businessId();
        List<BookableService> services = activeOnly
                ? repository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId)
                : repository.findByBusinessIdOrderByNameAsc(businessId);
        return services.stream().map(ServiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(Long id) {
        return ServiceResponse.from(requireForBusiness(currentTenant.businessId(), id));
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        currentTenant.requireOwner();
        BookableService service = new BookableService();
        service.setBusinessId(currentTenant.businessId());
        apply(service, request);
        return ServiceResponse.from(repository.save(service));
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) {
        currentTenant.requireOwner();
        BookableService service = requireForBusiness(currentTenant.businessId(), id);
        apply(service, request);
        return ServiceResponse.from(repository.save(service));
    }

    /**
     * Deletes only when nothing references the service. Past bookings must keep
     * pointing at what was actually delivered, so a service with history is
     * deactivated instead — the caller is told which to do.
     */
    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        BookableService service = requireForBusiness(currentTenant.businessId(), id);
        try {
            repository.delete(service);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.of(ErrorCode.SERVICE_IN_USE,
                    "This service has bookings and cannot be deleted. Deactivate it instead.");
        }
    }

    @Transactional
    public ServiceResponse setActive(Long id, boolean active) {
        currentTenant.requireOwner();
        BookableService service = requireForBusiness(currentTenant.businessId(), id);
        service.setActive(active);
        return ServiceResponse.from(repository.save(service));
    }

    private static void apply(BookableService service, ServiceRequest request) {
        service.setName(request.name().trim());
        service.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        service.setDurationMinutes(request.durationMinutes());
        service.setPrice(request.price());
        service.setCurrency(request.currency() == null ? "EUR" : request.currency());
        service.setBufferBeforeMinutes(request.bufferBeforeMinutes());
        service.setBufferAfterMinutes(request.bufferAfterMinutes());
        service.setRequiresStaff(request.requiresStaff());
        service.setRequiresResource(request.requiresResource());
        service.setActive(request.active());
    }
}
