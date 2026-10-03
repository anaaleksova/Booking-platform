package com.bookingsaas.resource;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.media.MediaService;
import com.bookingsaas.resource.dto.ResourceRequest;
import com.bookingsaas.resource.dto.ResourceResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Concrete reservable resources, their own schedules and which services they
 * may serve.
 *
 * <p>Resources of the employee type are created and renamed by the employee
 * module through the {@code *Managed} methods; the admin resource API refuses
 * to touch them so the two views cannot drift apart.
 */
@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceTypeService typeService;
    private final ResourceHourRepository hourRepository;
    private final ResourceServiceLinkRepository linkRepository;
    private final MediaService mediaService;
    private final CurrentTenant currentTenant;

    public ResourceService(ResourceRepository resourceRepository,
                           ResourceTypeService typeService,
                           ResourceHourRepository hourRepository,
                           ResourceServiceLinkRepository linkRepository,
                           MediaService mediaService,
                           CurrentTenant currentTenant) {
        this.resourceRepository = resourceRepository;
        this.typeService = typeService;
        this.hourRepository = hourRepository;
        this.linkRepository = linkRepository;
        this.mediaService = mediaService;
        this.currentTenant = currentTenant;
    }

    // --- cross-module lookups ---------------------------------------------

    @Transactional(readOnly = true)
    public Resource requireForBusiness(Long businessId, Long resourceId) {
        return resourceRepository.findByIdAndBusinessId(resourceId, businessId)
                .orElseThrow(() -> ApiException.notFound("Resource", resourceId));
    }

    @Transactional(readOnly = true)
    public List<Resource> byIds(Long businessId, Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : resourceRepository.findByBusinessIdAndIdInOrderByNameAsc(businessId, ids);
    }

    /** Active resources of the given types — the candidate pool for a service's requirements. */
    @Transactional(readOnly = true)
    public List<Resource> activeOfTypes(Long businessId, Collection<Long> typeIds) {
        return typeIds.isEmpty() ? List.of()
                : resourceRepository.findByBusinessIdAndResourceTypeIdInAndActiveTrueOrderByNameAsc(businessId, typeIds);
    }

    @Transactional(readOnly = true)
    public List<Resource> allActive(Long businessId) {
        return resourceRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId);
    }

    /** resourceId → service ids it is restricted to. Absent = unrestricted. */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> serviceRestrictions(Collection<Long> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return linkRepository.findByResourceIdIn(resourceIds).stream()
                .collect(Collectors.groupingBy(ResourceServiceLink::getResourceId,
                        Collectors.mapping(ResourceServiceLink::getServiceId, Collectors.toList())));
    }

    /** resourceId → own weekly schedule. Absent = follows business hours. */
    @Transactional(readOnly = true)
    public Map<Long, List<DayInterval>> hoursOf(Long businessId, Collection<Long> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return hourRepository.findByBusinessIdAndResourceIdIn(businessId, resourceIds).stream()
                .collect(Collectors.groupingBy(ResourceHour::getResourceId,
                        Collectors.mapping(ResourceService::toInterval, Collectors.toList())));
    }

    // --- employee-managed resources ---------------------------------------

    @Transactional
    public Resource createManaged(Long businessId, String name, Integer capacity, boolean active, UUID imageMediaId) {
        ResourceType employeeType = typeService.ensureEmployeeType(businessId);
        Resource resource = new Resource();
        resource.setBusinessId(businessId);
        resource.setResourceTypeId(employeeType.getId());
        resource.setName(name);
        resource.setCapacity(capacity);
        resource.setActive(active);
        resource.setImageMediaId(mediaService.requireOwned(businessId, imageMediaId));
        return resourceRepository.save(resource);
    }

    @Transactional
    public void syncManaged(Long businessId, Long resourceId, String name, Integer capacity, boolean active,
                            UUID imageMediaId) {
        Resource resource = requireForBusiness(businessId, resourceId);
        UUID previousImage = resource.getImageMediaId();
        resource.setName(name);
        resource.setCapacity(capacity);
        resource.setActive(active);
        resource.setImageMediaId(mediaService.requireOwned(businessId, imageMediaId));
        resourceRepository.saveAndFlush(resource);
        mediaService.discardIfReplaced(businessId, previousImage, resource.getImageMediaId());
    }

    /** Deletes an employee's resource; fails with the database's verdict if reservations reference it. */
    @Transactional
    public void deleteManaged(Long businessId, Long resourceId) {
        Resource resource = requireForBusiness(businessId, resourceId);
        UUID image = resource.getImageMediaId();
        linkRepository.deleteByResourceId(resourceId);
        hourRepository.deleteByResourceId(resourceId);
        resourceRepository.delete(resource);
        resourceRepository.flush();
        mediaService.discardIfReplaced(businessId, image, null);
    }

    @Transactional
    public List<DayInterval> replaceHoursOf(Long businessId, Long resourceId, List<DayInterval> intervals) {
        requireForBusiness(businessId, resourceId);
        DayInterval.validate(intervals);
        hourRepository.deleteByResourceId(resourceId);
        hourRepository.flush();
        if (intervals != null && !intervals.isEmpty()) {
            hourRepository.saveAll(intervals.stream()
                    .map(interval -> new ResourceHour(businessId, resourceId, interval.dayOfWeek(),
                            interval.startTime(), interval.endTime()))
                    .toList());
        }
        return hoursDto(resourceId);
    }

    @Transactional
    public List<Long> replaceServicesOf(Long businessId, Long resourceId, List<Long> serviceIds) {
        requireForBusiness(businessId, resourceId);
        linkRepository.deleteByResourceId(resourceId);
        linkRepository.flush();
        if (serviceIds == null || serviceIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinct = serviceIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        // Cross-tenant guard: a foreign service id is simply not counted.
        if (linkRepository.countOwnedServices(businessId, distinct) != distinct.size()) {
            throw ApiException.validation("One or more selected services do not belong to this business.");
        }
        linkRepository.saveAll(distinct.stream().map(id -> new ResourceServiceLink(resourceId, id)).toList());
        return serviceIdsOf(resourceId);
    }

    @Transactional(readOnly = true)
    public List<Long> serviceIdsOf(Long resourceId) {
        return linkRepository.findByResourceId(resourceId).stream()
                .map(ResourceServiceLink::getServiceId)
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DayInterval> hoursDto(Long resourceId) {
        return hourRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(resourceId).stream()
                .map(ResourceService::toInterval)
                .toList();
    }

    // --- admin API ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ResourceResponse> list(Long typeId) {
        Long businessId = currentTenant.businessId();
        List<Resource> resources = resourceRepository.findByBusinessIdOrderByNameAsc(businessId).stream()
                .filter(resource -> typeId == null || resource.getResourceTypeId().equals(typeId))
                .sorted(Comparator.comparing(Resource::getName, ResourceService::naturalCompare))
                .toList();
        if (resources.isEmpty()) {
            return List.of();
        }
        List<Long> ids = resources.stream().map(Resource::getId).toList();
        Map<Long, ResourceType> types = typeService.byIds(businessId,
                resources.stream().map(Resource::getResourceTypeId).distinct().toList());
        Map<Long, List<Long>> services = serviceRestrictions(ids);
        Map<Long, List<DayInterval>> hours = hoursOf(businessId, ids);

        return resources.stream()
                .map(resource -> ResourceResponse.from(resource, types.get(resource.getResourceTypeId()),
                        services.getOrDefault(resource.getId(), List.of()),
                        sorted(hours.getOrDefault(resource.getId(), List.of()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse get(Long id) {
        Long businessId = currentTenant.businessId();
        return toResponse(businessId, requireForBusiness(businessId, id));
    }

    @Transactional
    public ResourceResponse create(ResourceRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        requireNonEmployeeType(businessId, request.resourceTypeId());

        Resource resource = new Resource();
        resource.setBusinessId(businessId);
        apply(resource, request);
        resource.setImageMediaId(mediaService.requireOwned(businessId, request.imageMediaId()));
        Resource saved = resourceRepository.save(resource);
        replaceServicesOf(businessId, saved.getId(), request.serviceIds());
        return toResponse(businessId, saved);
    }

    @Transactional
    public ResourceResponse update(Long id, ResourceRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Resource resource = requireForBusiness(businessId, id);
        requireNotEmployeeManaged(businessId, resource);
        requireNonEmployeeType(businessId, request.resourceTypeId());

        UUID previousImage = resource.getImageMediaId();
        apply(resource, request);
        resource.setImageMediaId(mediaService.requireOwned(businessId, request.imageMediaId()));
        Resource saved = resourceRepository.saveAndFlush(resource);
        mediaService.discardIfReplaced(businessId, previousImage, saved.getImageMediaId());
        if (request.serviceIds() != null) {
            replaceServicesOf(businessId, id, request.serviceIds());
        }
        return toResponse(businessId, saved);
    }

    @Transactional
    public ResourceResponse setActive(Long id, boolean active) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Resource resource = requireForBusiness(businessId, id);
        requireNotEmployeeManaged(businessId, resource);
        resource.setActive(active);
        return toResponse(businessId, resourceRepository.save(resource));
    }

    @Transactional
    public List<DayInterval> replaceHours(Long id, List<DayInterval> intervals) {
        currentTenant.requireOwner();
        return replaceHoursOf(currentTenant.businessId(), id, intervals);
    }

    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Resource resource = requireForBusiness(businessId, id);
        requireNotEmployeeManaged(businessId, resource);
        try {
            deleteManaged(businessId, id);
        } catch (DataIntegrityViolationException ex) {
            // reservation_resource references resources with ON DELETE RESTRICT.
            throw ApiException.of(ErrorCode.RESOURCE_IN_USE,
                    "This resource has reservations and cannot be deleted. Deactivate it instead.");
        }
    }

    // --- internals ---------------------------------------------------------

    private ResourceResponse toResponse(Long businessId, Resource resource) {
        ResourceType type = typeService.requireForBusiness(businessId, resource.getResourceTypeId());
        return ResourceResponse.from(resource, type, serviceIdsOf(resource.getId()), hoursDto(resource.getId()));
    }

    private void requireNonEmployeeType(Long businessId, Long typeId) {
        if (typeService.requireForBusiness(businessId, typeId).isEmployeeType()) {
            throw ApiException.validation("Employees are added on the Employees page, not as resources.");
        }
    }

    private void requireNotEmployeeManaged(Long businessId, Resource resource) {
        if (typeService.requireForBusiness(businessId, resource.getResourceTypeId()).isEmployeeType()) {
            throw ApiException.validation("This resource represents an employee. Edit it on the Employees page.");
        }
    }

    private static void apply(Resource resource, ResourceRequest request) {
        resource.setResourceTypeId(request.resourceTypeId());
        resource.setName(request.name().trim());
        resource.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        resource.setCapacity(request.capacity());
        boolean shared = Boolean.TRUE.equals(request.sharedSeating());
        if (shared && request.capacity() == null) {
            throw ApiException.validation("A shared resource needs a capacity — how many people it seats.");
        }
        if (!shared && request.minPartySize() != null && request.capacity() != null
                && request.minPartySize() > request.capacity()) {
            throw ApiException.validation("The smallest group cannot be larger than the capacity.");
        }
        resource.setSharedSeating(shared);
        // A shared resource takes any group; leftover seats go to others.
        resource.setMinPartySize(shared ? null : request.minPartySize());
        resource.setActive(request.active());
    }

    private static List<DayInterval> sorted(List<DayInterval> intervals) {
        return intervals.stream()
                .sorted(Comparator.comparing(DayInterval::dayOfWeek).thenComparing(DayInterval::startTime))
                .toList();
    }

    static DayInterval toInterval(ResourceHour hour) {
        return new DayInterval((int) hour.getDayOfWeek(), hour.getStartTime(), hour.getEndTime());
    }

    /** "Room 2" before "Room 10". */
    static int naturalCompare(String a, String b) {
        String[] left = a.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        String[] right = b.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            int cmp;
            if (left[i].chars().allMatch(Character::isDigit) && right[i].chars().allMatch(Character::isDigit)
                    && left[i].length() < 10 && right[i].length() < 10) {
                cmp = Integer.compare(Integer.parseInt(left[i]), Integer.parseInt(right[i]));
            } else {
                cmp = left[i].compareToIgnoreCase(right[i]);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(left.length, right.length);
    }
}
