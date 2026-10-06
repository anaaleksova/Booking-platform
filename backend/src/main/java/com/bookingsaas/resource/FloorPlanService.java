package com.bookingsaas.resource;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.resource.dto.FloorPlanDtos.Decor;
import com.bookingsaas.resource.dto.FloorPlanDtos.FloorPlanRequest;
import com.bookingsaas.resource.dto.FloorPlanDtos.FloorPlanResponse;
import com.bookingsaas.resource.dto.FloorPlanDtos.FloorPlansRequest;
import com.bookingsaas.resource.dto.FloorPlanDtos.Item;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Optional maps of the place, per resource type — one per floor or area, so
 * a co-working space can draw each of its floors. A resource stands on at most
 * one of them; one left off every map still works and is shown in the list.
 */
@Service
public class FloorPlanService {

    private final FloorPlanRepository repository;
    private final ResourceTypeService typeService;
    private final ResourceService resourceService;
    private final CurrentTenant currentTenant;

    public FloorPlanService(FloorPlanRepository repository,
                            ResourceTypeService typeService,
                            ResourceService resourceService,
                            CurrentTenant currentTenant) {
        this.repository = repository;
        this.typeService = typeService;
        this.resourceService = resourceService;
        this.currentTenant = currentTenant;
    }

    /** Plans for the given types, by type id and in order — what the booking pages show. */
    @Transactional(readOnly = true)
    public Map<Long, List<FloorPlanResponse>> plansFor(Long businessId, Collection<Long> resourceTypeIds) {
        if (resourceTypeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<FloorPlanResponse>> byType = new LinkedHashMap<>();
        repository.findByBusinessIdAndResourceTypeIdInOrderByPositionAscIdAsc(businessId, resourceTypeIds)
                .forEach(plan -> byType.computeIfAbsent(plan.getResourceTypeId(), key -> new ArrayList<>())
                        .add(FloorPlanResponse.from(plan)));
        return byType;
    }

    /** Where a resource stands: the plan it is drawn on. */
    public record Area(Long id, String name) {
    }

    /**
     * Resource id → the floor plan it stands on, for the given types.
     * Resources on no plan are absent. The booking engine keeps combined
     * resources within one area.
     */
    @Transactional(readOnly = true)
    public Map<Long, Area> areasOf(Long businessId, Collection<Long> resourceTypeIds) {
        if (resourceTypeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Area> areas = new HashMap<>();
        for (FloorPlan plan : repository.findByBusinessIdAndResourceTypeIdInOrderByPositionAscIdAsc(businessId,
                resourceTypeIds)) {
            Area area = new Area(plan.getId(), plan.getName());
            plan.getItems().forEach(item -> areas.put(item.getResourceId(), area));
        }
        return areas;
    }

    @Transactional(readOnly = true)
    public List<FloorPlanResponse> list(Long resourceTypeId) {
        Long businessId = currentTenant.businessId();
        typeService.requireForBusiness(businessId, resourceTypeId);
        return repository.findByBusinessIdAndResourceTypeIdOrderByPositionAscIdAsc(businessId, resourceTypeId).stream()
                .map(FloorPlanResponse::from)
                .toList();
    }

    /**
     * Replaces every floor of the type in one go, so moving a desk from one
     * floor to another is a single, atomic save.
     */
    @Transactional
    public List<FloorPlanResponse> saveAll(Long resourceTypeId, FloorPlansRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        ResourceType type = typeService.requireForBusiness(businessId, resourceTypeId);
        if (type.isEmployeeType()) {
            throw ApiException.validation("Employees are not placed on a floor plan.");
        }
        validate(businessId, resourceTypeId, request.plans());

        Map<Long, FloorPlan> existing = repository
                .findByBusinessIdAndResourceTypeIdOrderByPositionAscIdAsc(businessId, resourceTypeId).stream()
                .collect(Collectors.toMap(FloorPlan::getId, Function.identity()));
        for (FloorPlanRequest plan : request.plans()) {
            if (plan.id() != null && !existing.containsKey(plan.id())) {
                throw ApiException.notFound("Floor plan", plan.id());
            }
        }

        // Hibernate inserts before it deletes: empty every floor and drop the
        // removed ones first, so a desk moving between floors never meets itself.
        Set<Long> kept = request.plans().stream().map(FloorPlanRequest::id).filter(id -> id != null)
                .collect(Collectors.toSet());
        existing.values().forEach(plan -> {
            plan.getItems().clear();
            plan.getDecor().clear();
        });
        existing.values().stream().filter(plan -> !kept.contains(plan.getId())).forEach(repository::delete);
        repository.flush();

        List<FloorPlan> saved = new ArrayList<>();
        for (int position = 0; position < request.plans().size(); position++) {
            FloorPlanRequest source = request.plans().get(position);
            FloorPlan plan = source.id() == null ? newPlan(businessId, resourceTypeId) : existing.get(source.id());
            plan.setName(source.name().trim());
            plan.setPosition(position);
            plan.setAspectRatio(source.aspectRatio());
            fill(plan, source);
            saved.add(repository.save(plan));
        }
        repository.flush();
        return saved.stream().map(FloorPlanResponse::from).toList();
    }

    private static FloorPlan newPlan(Long businessId, Long resourceTypeId) {
        FloorPlan plan = new FloorPlan();
        plan.setBusinessId(businessId);
        plan.setResourceTypeId(resourceTypeId);
        return plan;
    }

    private static void fill(FloorPlan plan, FloorPlanRequest source) {
        for (Item item : source.items()) {
            FloorPlanItem spot = new FloorPlanItem();
            spot.setResourceId(item.resourceId());
            spot.setX(item.x());
            spot.setY(item.y());
            spot.setWidth(item.width());
            spot.setHeight(item.height());
            spot.setShape(item.shape());
            plan.getItems().add(spot);
        }
        for (Decor decor : source.decorOrEmpty()) {
            FloorPlanDecor piece = new FloorPlanDecor();
            piece.setKind(decor.kind());
            piece.setLabel(decor.label() == null || decor.label().isBlank() ? null : decor.label().trim());
            piece.setTone(decor.kind() == FloorPlanDecor.Kind.ZONE ? decor.tone() : null);
            piece.setX(decor.x());
            piece.setY(decor.y());
            piece.setWidth(decor.width());
            piece.setHeight(decor.height());
            plan.getDecor().add(piece);
        }
    }

    /** Distinct floor names; every spot a resource of this business and type, on one floor only. */
    private void validate(Long businessId, Long resourceTypeId, List<FloorPlanRequest> plans) {
        Set<String> names = new HashSet<>();
        Map<Long, String> floorOf = new HashMap<>();
        for (FloorPlanRequest plan : plans) {
            String name = plan.name().trim();
            if (!names.add(name.toLowerCase())) {
                throw ApiException.validation("Two floors are called \"" + name + "\". Give each floor its own name.");
            }
            for (Item item : plan.items()) {
                String previous = floorOf.putIfAbsent(item.resourceId(), name);
                if (previous != null) {
                    throw ApiException.validation(previous.equals(name)
                            ? "Each resource can be placed on \"" + name + "\" only once."
                            : "A resource cannot be on both \"" + previous + "\" and \"" + name + "\".");
                }
            }
        }
        List<Resource> resources = resourceService.byIds(businessId, floorOf.keySet());
        if (resources.size() != floorOf.size()
                || resources.stream().anyMatch(resource -> !resource.getResourceTypeId().equals(resourceTypeId))) {
            throw ApiException.validation("Only resources of this type can be placed on its floor plans.");
        }
    }
}
