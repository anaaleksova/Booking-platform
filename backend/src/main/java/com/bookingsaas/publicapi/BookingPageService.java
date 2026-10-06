package com.bookingsaas.publicapi;

import com.bookingsaas.business.Business;
import com.bookingsaas.media.Media;
import com.bookingsaas.publicapi.dto.PublicServiceResponse;
import com.bookingsaas.publicapi.dto.PublicServiceResponse.ChoosableRequirement;
import com.bookingsaas.publicapi.dto.PublicServiceResponse.Decor;
import com.bookingsaas.publicapi.dto.PublicServiceResponse.FloorPlan;
import com.bookingsaas.publicapi.dto.PublicServiceResponse.Option;
import com.bookingsaas.publicapi.dto.PublicServiceResponse.Spot;
import com.bookingsaas.resource.FloorPlanService;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import com.bookingsaas.resource.dto.FloorPlanDtos.FloorPlanResponse;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import com.bookingsaas.service.ServiceResourceRequirement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles what the customer-facing pages show: services with the choices a
 * customer can make, including the floor plan to make them on.
 */
@Service
public class BookingPageService {

    private final ServiceCatalogService serviceCatalog;
    private final ResourceService resourceService;
    private final FloorPlanService floorPlanService;

    public BookingPageService(ServiceCatalogService serviceCatalog,
                              ResourceService resourceService,
                              FloorPlanService floorPlanService) {
        this.serviceCatalog = serviceCatalog;
        this.resourceService = resourceService;
        this.floorPlanService = floorPlanService;
    }

    @Transactional(readOnly = true)
    public List<PublicServiceResponse> services(Business business) {
        return serviceCatalog.activeServicesOf(business.getId()).stream()
                .sorted(Comparator.comparing((BookableService service) -> service.getCategory() == null ? "\uffff"
                        : service.getCategory()).thenComparing(BookableService::getName))
                .map(service -> toPublic(business, service))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicServiceResponse service(Business business, Long serviceId) {
        return toPublic(business, serviceCatalog.requireBookable(business.getId(), serviceId));
    }

    private PublicServiceResponse toPublic(Business business, BookableService service) {
        List<ChoosableRequirement> choosable = List.of();
        if (service.isAllowCustomerResourceSelection()) {
            List<ServiceResourceRequirement> requirements = serviceCatalog.requirementsOf(service.getId()).stream()
                    .filter(ServiceResourceRequirement::isCustomerCanChoose)
                    .toList();
            Map<Long, String> typeNames = new HashMap<>();
            serviceCatalog.requirementViews(business.getId(), service.getId())
                    .forEach(view -> typeNames.put(view.id(), view.resourceTypeName()));
            List<Resource> candidates = resourceService.activeOfTypes(business.getId(),
                    requirements.stream().map(ServiceResourceRequirement::getResourceTypeId).toList());
            Map<Long, List<Long>> restrictions = resourceService.serviceRestrictions(
                    candidates.stream().map(Resource::getId).toList());

            Map<Long, List<FloorPlanResponse>> plans = floorPlanService.plansFor(business.getId(),
                    requirements.stream().map(ServiceResourceRequirement::getResourceTypeId).distinct().toList());

            choosable = requirements.stream()
                    .map(requirement -> {
                        List<Option> options = candidates.stream()
                                .filter(resource -> resource.getResourceTypeId().equals(requirement.getResourceTypeId()))
                                .filter(resource -> {
                                    List<Long> allowed = restrictions.get(resource.getId());
                                    return allowed == null || allowed.contains(service.getId());
                                })
                                .map(resource -> new Option(resource.getId(), resource.getName(),
                                        resource.getDescription(), Media.urlOf(resource.getImageMediaId()),
                                        resource.getCapacity(), resource.isSharedSeating(), resource.getMinPartySize()))
                                .toList();
                        return new ChoosableRequirement(requirement.getId(),
                                typeNames.getOrDefault(requirement.getId(), "Resource"), requirement.getQuantity(),
                                requirement.isCombineResources(), options, plans.getOrDefault(requirement.getResourceTypeId(), List.of()).stream()
                                        .map(plan -> publicPlan(plan, options))
                                        .filter(Objects::nonNull)
                                        .toList());
                    })
                    .toList();
        }
        return new PublicServiceResponse(service.getId(), service.getName(), service.getDescription(),
                service.getCategory(), Media.urlOf(service.getImageMediaId()), service.getDurationMinutes(),
                service.getPrice(), service.isPricePerParticipant(), service.getMaxParticipants(),
                business.getCurrency(), choosable);
    }

    /**
     * The map as customers see it: only spots of resources they can choose
     * for this service. A map with nothing on it is no map at all.
     */
    private static FloorPlan publicPlan(FloorPlanResponse plan, List<Option> options) {
        if (plan == null) {
            return null;
        }
        Set<Long> choosable = options.stream().map(Option::id).collect(Collectors.toSet());
        List<Spot> spots = plan.items().stream()
                .filter(item -> choosable.contains(item.resourceId()))
                .map(item -> new Spot(item.resourceId(), item.x(), item.y(), item.width(), item.height(),
                        item.shape().name()))
                .toList();
        List<Decor> decor = plan.decor().stream()
                .map(piece -> new Decor(piece.kind().name(), piece.label(),
                        piece.tone() == null ? null : piece.tone().name(), piece.x(), piece.y(), piece.width(),
                        piece.height()))
                .toList();
        return spots.isEmpty() ? null : new FloorPlan(plan.id(), plan.name(), plan.aspectRatio(), spots, decor);
    }
}
