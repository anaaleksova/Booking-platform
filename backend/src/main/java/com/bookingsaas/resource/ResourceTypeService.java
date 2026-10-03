package com.bookingsaas.resource;

import com.bookingsaas.business.BusinessCreatedEvent;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.resource.dto.ResourceTypeRequest;
import com.bookingsaas.resource.dto.ResourceTypeResponse;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Business-defined resource types, including the one that represents employees. */
@Service
public class ResourceTypeService {

    private final ResourceTypeRepository repository;
    private final ResourceRepository resourceRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final CurrentTenant currentTenant;

    public ResourceTypeService(ResourceTypeRepository repository,
                               ResourceRepository resourceRepository,
                               FloorPlanRepository floorPlanRepository,
                               CurrentTenant currentTenant) {
        this.repository = repository;
        this.resourceRepository = resourceRepository;
        this.floorPlanRepository = floorPlanRepository;
        this.currentTenant = currentTenant;
    }

    // --- cross-module lookups ---------------------------------------------

    @Transactional(readOnly = true)
    public ResourceType requireForBusiness(Long businessId, Long typeId) {
        return repository.findByIdAndBusinessId(typeId, businessId)
                .orElseThrow(() -> ApiException.notFound("Resource type", typeId));
    }

    @Transactional(readOnly = true)
    public Map<Long, ResourceType> byIds(Long businessId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findByBusinessIdAndIdIn(businessId, ids).stream()
                .collect(Collectors.toMap(ResourceType::getId, Function.identity()));
    }

    /**
     * The business's employee type, created on first use. Every business gets
     * one at registration; this also covers businesses created by other means.
     */
    @Transactional
    public ResourceType ensureEmployeeType(Long businessId) {
        return repository.findByBusinessIdAndEmployeeTypeTrue(businessId).orElseGet(() -> {
            ResourceType type = new ResourceType();
            type.setBusinessId(businessId);
            type.setName(ResourceType.DEFAULT_EMPLOYEE_TYPE_NAME);
            type.setDescription("Team members. Managed on the Employees page.");
            type.setEmployeeType(true);
            return repository.save(type);
        });
    }

    @EventListener
    public void onBusinessCreated(BusinessCreatedEvent event) {
        ensureEmployeeType(event.businessId());
    }

    // --- admin API ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ResourceTypeResponse> list() {
        Long businessId = currentTenant.businessId();
        Map<Long, Long> counts = resourceRepository.findByBusinessIdOrderByNameAsc(businessId).stream()
                .collect(Collectors.groupingBy(Resource::getResourceTypeId, Collectors.counting()));
        Map<Long, Long> floors = floorPlanRepository.findByBusinessId(businessId).stream()
                .collect(Collectors.groupingBy(FloorPlan::getResourceTypeId, Collectors.counting()));
        return repository.findByBusinessIdOrderByNameAsc(businessId).stream()
                .map(type -> ResourceTypeResponse.from(type, counts.getOrDefault(type.getId(), 0L),
                        floors.getOrDefault(type.getId(), 0L)))
                .toList();
    }

    @Transactional
    public ResourceTypeResponse create(ResourceTypeRequest request) {
        currentTenant.requireOwner();
        ResourceType type = new ResourceType();
        type.setBusinessId(currentTenant.businessId());
        apply(type, request);
        return ResourceTypeResponse.from(save(type), 0, 0);
    }

    @Transactional
    public ResourceTypeResponse update(Long id, ResourceTypeRequest request) {
        currentTenant.requireOwner();
        ResourceType type = requireForBusiness(currentTenant.businessId(), id);
        if (type.isEmployeeType() && !request.active()) {
            throw ApiException.validation("The employee type cannot be deactivated. Deactivate employees instead.");
        }
        apply(type, request);
        return ResourceTypeResponse.from(save(type), resourceRepository.countByResourceTypeId(id),
                floorPlanRepository.findByBusinessIdAndResourceTypeIdOrderByPositionAscIdAsc(type.getBusinessId(), id).size());
    }

    /**
     * Deletes a type nothing uses. A type with resources, or one that a
     * service requires, would leave dangling configuration — the caller is
     * told to remove those first or deactivate the type.
     */
    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        ResourceType type = requireForBusiness(currentTenant.businessId(), id);
        if (type.isEmployeeType()) {
            throw ApiException.of(ErrorCode.RESOURCE_TYPE_IN_USE, "The employee type cannot be deleted.");
        }
        if (resourceRepository.countByResourceTypeId(id) > 0) {
            throw ApiException.of(ErrorCode.RESOURCE_TYPE_IN_USE,
                    "This type still has resources. Delete or move them first, or deactivate the type.");
        }
        try {
            repository.delete(type);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.of(ErrorCode.RESOURCE_TYPE_IN_USE,
                    "A service requires this type. Remove it from the service first, or deactivate the type.");
        }
    }

    private static void apply(ResourceType type, ResourceTypeRequest request) {
        type.setName(request.name().trim());
        type.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        type.setActive(request.active());
    }

    private ResourceType save(ResourceType type) {
        try {
            ResourceType saved = repository.save(type);
            repository.flush();
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.of(ErrorCode.DUPLICATE_RESOURCE_TYPE,
                    "A resource type named '" + type.getName() + "' already exists.");
        }
    }
}
