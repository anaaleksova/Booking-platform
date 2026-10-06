package com.bookingsaas.service;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.media.MediaService;
import com.bookingsaas.resource.ResourceType;
import com.bookingsaas.resource.ResourceTypeService;
import com.bookingsaas.service.dto.ServiceDeletionResponse;
import com.bookingsaas.service.dto.ServiceRequest;
import com.bookingsaas.service.dto.ServiceResponse;
import com.bookingsaas.service.dto.ServiceResponse.RequirementResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The bookable catalogue and each service's resource requirements, scoped to the caller's tenant. */
@Service
public class ServiceCatalogService {

    private final BookableServiceRepository repository;
    private final ServiceResourceRequirementRepository requirementRepository;
    private final ResourceTypeService resourceTypeService;
    private final MediaService mediaService;
    private final CurrentTenant currentTenant;

    public ServiceCatalogService(BookableServiceRepository repository,
                                 ServiceResourceRequirementRepository requirementRepository,
                                 ResourceTypeService resourceTypeService,
                                 MediaService mediaService,
                                 CurrentTenant currentTenant) {
        this.repository = repository;
        this.requirementRepository = requirementRepository;
        this.resourceTypeService = resourceTypeService;
        this.mediaService = mediaService;
        this.currentTenant = currentTenant;
    }

    // --- cross-module lookups ---------------------------------------------

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

    @Transactional(readOnly = true)
    public List<BookableService> byIds(Long businessId, List<Long> ids) {
        return ids.isEmpty() ? List.of() : repository.findByBusinessIdAndIdIn(businessId, ids);
    }

    @Transactional(readOnly = true)
    public List<ServiceResourceRequirement> requirementsOf(Long serviceId) {
        return requirementRepository.findByServiceIdOrderByPositionAscIdAsc(serviceId);
    }

    /** Requirements with their type names resolved, in display order. */
    @Transactional(readOnly = true)
    public List<RequirementResponse> requirementViews(Long businessId, Long serviceId) {
        return views(businessId, requirementsOf(serviceId));
    }

    // --- admin API ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(boolean activeOnly) {
        Long businessId = currentTenant.businessId();
        List<BookableService> services = activeOnly
                ? repository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId)
                : repository.findByBusinessIdOrderByNameAsc(businessId);
        if (services.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ServiceResourceRequirement>> byService = requirementRepository
                .findByServiceIdInOrderByPositionAscIdAsc(services.stream().map(BookableService::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ServiceResourceRequirement::getServiceId));
        Map<Long, ResourceType> types = resourceTypeService.byIds(businessId, byService.values().stream()
                .flatMap(Collection::stream).map(ServiceResourceRequirement::getResourceTypeId).distinct().toList());

        return services.stream()
                .map(service -> ServiceResponse.from(service,
                        byService.getOrDefault(service.getId(), List.of()).stream()
                                .map(requirement -> view(requirement, types))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(Long id) {
        Long businessId = currentTenant.businessId();
        BookableService service = requireForBusiness(businessId, id);
        return ServiceResponse.from(service, requirementViews(businessId, id));
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        BookableService service = new BookableService();
        service.setBusinessId(businessId);
        apply(service, request);
        service.setImageMediaId(mediaService.requireOwned(businessId, request.imageMediaId()));
        BookableService saved = repository.save(service);
        replaceRequirements(businessId, saved.getId(), request.requirements());
        return ServiceResponse.from(saved, requirementViews(businessId, saved.getId()));
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        BookableService service = requireForBusiness(businessId, id);
        UUID previousImage = service.getImageMediaId();
        apply(service, request);
        service.setImageMediaId(mediaService.requireOwned(businessId, request.imageMediaId()));
        BookableService saved = repository.saveAndFlush(service);
        mediaService.discardIfReplaced(businessId, previousImage, saved.getImageMediaId());
        replaceRequirements(businessId, id, request.requirements());
        return ServiceResponse.from(saved, requirementViews(businessId, id));
    }

    @Transactional
    public ServiceResponse setActive(Long id, boolean active) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        BookableService service = requireForBusiness(businessId, id);
        service.setActive(active);
        return ServiceResponse.from(repository.save(service), requirementViews(businessId, id));
    }

    /**
     * Deletes the service when nothing references it. Past reservations must
     * keep pointing at what was actually delivered, so a service with history
     * is deactivated instead — the response says which happened.
     */
    @Transactional
    public ServiceDeletionResponse delete(Long id) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        BookableService service = requireForBusiness(businessId, id);
        if (repository.hasReservations(id)) {
            service.setActive(false);
            repository.save(service);
            return new ServiceDeletionResponse(false, true,
                    "The service has reservations, so it was deactivated instead of deleted.");
        }
        try {
            UUID image = service.getImageMediaId();
            requirementRepository.deleteByServiceId(id);
            repository.delete(service);
            repository.flush();
            mediaService.discardIfReplaced(businessId, image, null);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.of(ErrorCode.SERVICE_IN_USE,
                    "This service is still referenced and cannot be deleted. Deactivate it instead.");
        }
        return new ServiceDeletionResponse(true, false, "The service was deleted.");
    }

    // --- internals ---------------------------------------------------------

    private void replaceRequirements(Long businessId, Long serviceId,
                                     List<ServiceRequest.RequirementRequest> requested) {
        Set<Long> typeIds = new HashSet<>();
        for (ServiceRequest.RequirementRequest requirement : requested) {
            if (!typeIds.add(requirement.resourceTypeId())) {
                throw ApiException.validation("Each resource type can appear only once in a service's requirements.");
            }
        }
        Map<Long, ResourceType> owned = resourceTypeService.byIds(businessId, typeIds);
        if (owned.size() != typeIds.size()) {
            throw ApiException.validation("One or more resource types do not belong to this business.");
        }

        requirementRepository.deleteByServiceId(serviceId);
        requirementRepository.flush();
        int position = 0;
        for (ServiceRequest.RequirementRequest line : requested) {
            ServiceResourceRequirement requirement = new ServiceResourceRequirement();
            requirement.setServiceId(serviceId);
            requirement.setResourceTypeId(line.resourceTypeId());
            requirement.setQuantity(line.quantity());
            requirement.setRequired(line.required());
            requirement.setCustomerCanChoose(line.customerCanChoose());
            requirement.setCombineResources(line.combines());
            requirement.setPosition(position++);
            requirementRepository.save(requirement);
        }
    }

    private List<RequirementResponse> views(Long businessId, List<ServiceResourceRequirement> requirements) {
        Map<Long, ResourceType> types = resourceTypeService.byIds(businessId,
                requirements.stream().map(ServiceResourceRequirement::getResourceTypeId).distinct().toList());
        return requirements.stream().map(requirement -> view(requirement, types)).toList();
    }

    private static RequirementResponse view(ServiceResourceRequirement requirement, Map<Long, ResourceType> types) {
        ResourceType type = types.get(requirement.getResourceTypeId());
        return new RequirementResponse(requirement.getId(), requirement.getResourceTypeId(),
                type == null ? null : type.getName(), requirement.getQuantity(), requirement.isRequired(),
                requirement.isCustomerCanChoose(), requirement.isCombineResources());
    }

    private static void apply(BookableService service, ServiceRequest request) {
        service.setName(request.name().trim());
        service.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        service.setCategory(request.category() == null || request.category().isBlank()
                ? null : request.category().trim());
        service.setDurationMinutes(request.durationMinutes());
        service.setPrice(request.price());
        service.setPricePerParticipant(request.pricePerParticipant());
        service.setMaxParticipants(request.maxParticipants());
        service.setBufferBeforeMinutes(request.bufferBeforeMinutes());
        service.setBufferAfterMinutes(request.bufferAfterMinutes());
        service.setAllowCustomerResourceSelection(request.allowCustomerResourceSelection());
        service.setActive(request.active());
    }
}
