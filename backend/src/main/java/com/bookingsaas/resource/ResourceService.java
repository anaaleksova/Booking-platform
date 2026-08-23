package com.bookingsaas.resource;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.resource.dto.ResourceRequest;
import com.bookingsaas.resource.dto.ResourceResponse;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.BookableServiceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Bookable resources and which services they can host. */
@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceServiceLinkRepository linkRepository;
    private final BookableServiceRepository serviceRepository;
    private final CurrentTenant currentTenant;

    public ResourceService(ResourceRepository resourceRepository,
                           ResourceServiceLinkRepository linkRepository,
                           BookableServiceRepository serviceRepository,
                           CurrentTenant currentTenant) {
        this.resourceRepository = resourceRepository;
        this.linkRepository = linkRepository;
        this.serviceRepository = serviceRepository;
        this.currentTenant = currentTenant;
    }

    // --- cross-module lookups ---------------------------------------------

    @Transactional(readOnly = true)
    public Resource requireForBusiness(Long businessId, Long resourceId) {
        return resourceRepository.findByIdAndBusinessId(resourceId, businessId)
                .orElseThrow(() -> ApiException.notFound("Resource", resourceId));
    }

    /**
     * Resources that can host a service: active, and either explicitly linked
     * to it or — when nothing is linked — any of them.
     */
    @Transactional(readOnly = true)
    public List<Resource> eligibleResourcesFor(Long businessId, Long serviceId) {
        List<Long> assigned = linkRepository.findResourceIdsByServiceId(serviceId);
        if (assigned.isEmpty()) {
            return resourceRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId);
        }
        return resourceRepository.findByBusinessIdAndIdInOrderByNameAsc(businessId, assigned).stream()
                .filter(Resource::isActive)
                .toList();
    }

    // --- dashboard API -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<ResourceResponse> list(boolean activeOnly) {
        Long businessId = currentTenant.businessId();
        List<Resource> resources = activeOnly
                ? resourceRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId)
                : resourceRepository.findByBusinessIdOrderByNameAsc(businessId);
        if (resources.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> servicesByResource = linkRepository
                .findByResourceIdIn(resources.stream().map(Resource::getId).toList()).stream()
                .collect(Collectors.groupingBy(ResourceServiceLink::getResourceId,
                        Collectors.mapping(ResourceServiceLink::getServiceId, Collectors.toList())));

        return resources.stream()
                .map(resource -> ResourceResponse.from(resource,
                        servicesByResource.getOrDefault(resource.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse get(Long id) {
        Resource resource = requireForBusiness(currentTenant.businessId(), id);
        return ResourceResponse.from(resource, serviceIdsOf(id));
    }

    @Transactional
    public ResourceResponse create(ResourceRequest request) {
        currentTenant.requireOwner();
        Resource resource = new Resource();
        resource.setBusinessId(currentTenant.businessId());
        apply(resource, request);
        Resource saved = resourceRepository.save(resource);
        replaceServices(saved, request.serviceIds());
        return ResourceResponse.from(saved, serviceIdsOf(saved.getId()));
    }

    @Transactional
    public ResourceResponse update(Long id, ResourceRequest request) {
        currentTenant.requireOwner();
        Resource resource = requireForBusiness(currentTenant.businessId(), id);
        apply(resource, request);
        Resource saved = resourceRepository.save(resource);
        if (request.serviceIds() != null) {
            replaceServices(saved, request.serviceIds());
        }
        return ResourceResponse.from(saved, serviceIdsOf(id));
    }

    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        Resource resource = requireForBusiness(currentTenant.businessId(), id);
        linkRepository.deleteByResourceId(id);
        try {
            resourceRepository.delete(resource);
            resourceRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.of(ErrorCode.RESOURCE_IN_USE,
                    "This resource has bookings and cannot be deleted. Deactivate it instead.");
        }
    }

    @Transactional
    public ResourceResponse replaceServices(Long resourceId, List<Long> serviceIds) {
        currentTenant.requireOwner();
        Resource resource = requireForBusiness(currentTenant.businessId(), resourceId);
        replaceServices(resource, serviceIds == null ? List.of() : serviceIds);
        return ResourceResponse.from(resource, serviceIdsOf(resourceId));
    }

    // --- internals ---------------------------------------------------------

    private static void apply(Resource resource, ResourceRequest request) {
        resource.setName(request.name().trim());
        resource.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        resource.setActive(request.active());
    }

    private void replaceServices(Resource resource, List<Long> serviceIds) {
        linkRepository.deleteByResourceId(resource.getId());
        linkRepository.flush();
        if (serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = serviceIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> owned = serviceRepository.findByBusinessIdAndIdIn(resource.getBusinessId(), List.copyOf(distinct))
                .stream()
                .map(BookableService::getId)
                .collect(Collectors.toSet());
        if (owned.size() != distinct.size()) {
            throw ApiException.validation("One or more selected services do not belong to this business.");
        }
        linkRepository.saveAll(owned.stream().map(id -> new ResourceServiceLink(resource.getId(), id)).toList());
    }

    private List<Long> serviceIdsOf(Long resourceId) {
        return linkRepository.findByResourceId(resourceId).stream()
                .map(ResourceServiceLink::getServiceId)
                .toList();
    }
}
