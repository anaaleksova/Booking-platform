package com.bookingsaas.availability;

import com.bookingsaas.availability.SlotEvaluation.RequirementOption;
import com.bookingsaas.availability.dto.AvailableSlot;
import com.bookingsaas.availability.dto.DayAvailability;
import com.bookingsaas.availability.dto.DaySlotsResponse;
import com.bookingsaas.availability.dto.SlotResponse;
import com.bookingsaas.availability.dto.SlotResponse.AllocationOption;
import com.bookingsaas.availability.dto.SlotResponse.ResourceOption;
import com.bookingsaas.availability.dto.SlotResponse.SlotRequirement;
import com.bookingsaas.availability.spi.BookedIntervalProvider;
import com.bookingsaas.availability.spi.OccupiedInterval;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.business.OpeningHoursService;
import com.bookingsaas.common.domain.TimeRange;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.resource.FloorPlanService;
import com.bookingsaas.resource.FloorPlanService.Area;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import com.bookingsaas.resource.ResourceType;
import com.bookingsaas.resource.ResourceTypeService;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import com.bookingsaas.service.ServiceResourceRequirement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads everything the {@link AvailabilityEngine} needs and hands back slots
 * or allocations.
 *
 * <p>Deliberately the single entry point: the customer pages, the admin panel
 * and the reservation transaction all call these methods, so no caller can
 * end up with its own idea of what is free.
 */
@Service
public class AvailabilityService {

    private final AvailabilityEngine engine;
    private final BusinessService businessService;
    private final OpeningHoursService openingHoursService;
    private final ServiceCatalogService serviceCatalog;
    private final ResourceService resourceService;
    private final ResourceTypeService resourceTypeService;
    private final BlockedTimeService blockedTimeService;
    private final BookedIntervalProvider bookedIntervalProvider;
    private final FloorPlanService floorPlanService;
    private final Clock clock;

    public AvailabilityService(AvailabilityEngine engine,
                               BusinessService businessService,
                               OpeningHoursService openingHoursService,
                               ServiceCatalogService serviceCatalog,
                               ResourceService resourceService,
                               ResourceTypeService resourceTypeService,
                               BlockedTimeService blockedTimeService,
                               BookedIntervalProvider bookedIntervalProvider,
                               FloorPlanService floorPlanService,
                               Clock clock) {
        this.engine = engine;
        this.businessService = businessService;
        this.openingHoursService = openingHoursService;
        this.serviceCatalog = serviceCatalog;
        this.resourceService = resourceService;
        this.resourceTypeService = resourceTypeService;
        this.blockedTimeService = blockedTimeService;
        this.bookedIntervalProvider = bookedIntervalProvider;
        this.floorPlanService = floorPlanService;
        this.clock = clock;
    }

    // --- slot search -------------------------------------------------------

    /**
     * @param customerFacing true for the public pages: lead times apply and
     *                       free resources are only listed for requirements
     *                       the customer may choose
     */
    @Transactional(readOnly = true)
    public List<DaySlotsResponse> search(Long businessId, AvailabilityQuery query, boolean customerFacing) {
        LocalDate from = query.from();
        LocalDate to = query.to() == null ? from : query.to();
        if (to.isBefore(from)) {
            throw ApiException.validation("'to' must not be before 'from'.");
        }
        if (from.plusDays(AvailabilityEngine.MAX_DAYS_PER_QUERY).isBefore(to)) {
            throw ApiException.validation("Availability can be requested for at most "
                    + AvailabilityEngine.MAX_DAYS_PER_QUERY + " days at a time.");
        }

        BookableService service = serviceCatalog.requireBookable(businessId, query.serviceId());
        Loaded loaded = load(businessId, service, query.participants(), query.excludeReservationId(),
                customerFacing, from, to);
        return engine.availableSlots(loaded.context(), from, to).stream()
                .map(day -> toResponse(day, loaded, customerFacing))
                .toList();
    }

    /**
     * What is free at one exact time for an existing reservation's service —
     * the choices behind "change assigned resources" in the admin panel.
     */
    @Transactional(readOnly = true)
    public SlotResponse optionsAt(Long businessId, BookableService service, Instant start, int participants,
                                  Long excludeReservationId) {
        LocalDate date = LocalDate.ofInstant(start, businessService.zoneOf(businessId));
        Loaded loaded = load(businessId, service, participants, excludeReservationId, false, date, date);
        SlotEvaluation evaluation = engine.evaluate(loaded.context(), start);
        if (!evaluation.available()) {
            throw evaluation.toException();
        }
        return toSlot(new AvailableSlot(java.time.LocalTime.from(start.atZone(loaded.context().zone())), start,
                start.plus(service.duration()), evaluation.options()), loaded, false);
    }

    // --- allocation, used by reservations ----------------------------------

    /**
     * Validates one exact start time and fixes the concrete resources. Called
     * inside the reservation transaction — never from a controller — so the
     * answer cannot go stale between check and insert.
     *
     * @param chosen          requirement id → picked resource ids; anything not
     *                        picked is assigned automatically
     * @param customerFacing  true for customers: lead times apply and only
     *                        customer-choosable requirements may be picked
     */
    // Read-only: a rejected slot must not mark the caller's transaction
    // rollback-only, so the caller can fall back (e.g. to automatic assignment).
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public ResourceAllocation allocate(Long businessId,
                                       BookableService service,
                                       Instant start,
                                       int participants,
                                       Map<Long, List<Long>> chosen,
                                       Long excludeReservationId,
                                       boolean customerFacing) {
        LocalDate date = LocalDate.ofInstant(start, businessService.zoneOf(businessId));
        Loaded loaded = load(businessId, service, participants, excludeReservationId, customerFacing, date, date);
        Map<Long, List<Long>> selection = chosen == null ? Map.of() : chosen;

        if (customerFacing && !selection.isEmpty()) {
            Map<Long, RequirementSpec> byId = loaded.context().service().requirements().stream()
                    .collect(Collectors.toMap(RequirementSpec::requirementId, Function.identity()));
            for (Map.Entry<Long, List<Long>> entry : selection.entrySet()) {
                RequirementSpec requirement = byId.get(entry.getKey());
                if (requirement != null && !requirement.customerChoosable() && !entry.getValue().isEmpty()) {
                    throw ApiException.of(ErrorCode.RESOURCE_SELECTION_NOT_ALLOWED,
                            requirement.resourceTypeName() + " is assigned by the business and cannot be chosen.");
                }
            }
        }
        return engine.allocate(loaded.context(), start, selection);
    }

    // --- context assembly --------------------------------------------------

    /** The context plus the resource rows and areas behind it, which the responses need for names. */
    private record Loaded(AvailabilityContext context, Map<Long, Resource> resources, Map<Long, Area> areas) {
    }

    private Loaded load(Long businessId,
                        BookableService service,
                        int participants,
                        Long excludeReservationId,
                        boolean customerFacing,
                        LocalDate from,
                        LocalDate to) {
        if (participants < 1 || participants > service.getMaxParticipants()) {
            throw ApiException.of(ErrorCode.INVALID_PARTICIPANTS, service.getMaxParticipants() == 1
                    ? "This service is for one participant."
                    : "Choose between 1 and " + service.getMaxParticipants() + " participants.");
        }

        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();
        BusinessSettings settings = businessService.settingsOf(businessId);

        List<ServiceResourceRequirement> requirements = serviceCatalog.requirementsOf(service.getId());
        List<Long> typeIds = requirements.stream().map(ServiceResourceRequirement::getResourceTypeId).toList();
        Map<Long, ResourceType> types = resourceTypeService.byIds(businessId, typeIds);

        // Inactive types contribute no candidates: a required requirement on
        // one makes the service unavailable, which is the honest answer.
        List<Resource> resources = resourceService.activeOfTypes(businessId, typeIds).stream()
                .filter(resource -> types.containsKey(resource.getResourceTypeId())
                        && types.get(resource.getResourceTypeId()).isActive())
                .toList();
        List<Long> resourceIds = resources.stream().map(Resource::getId).toList();
        Map<Long, Area> areas = floorPlanService.areasOf(businessId, typeIds);
        Map<Long, List<Long>> restrictions = resourceService.serviceRestrictions(resourceIds);

        List<RequirementSpec> requirementSpecs = requirements.stream()
                .map(requirement -> new RequirementSpec(
                        requirement.getId(),
                        requirement.getResourceTypeId(),
                        types.containsKey(requirement.getResourceTypeId())
                                ? types.get(requirement.getResourceTypeId()).getName() : "resource",
                        requirement.getQuantity(),
                        requirement.isRequired(),
                        service.isAllowCustomerResourceSelection() && requirement.isCustomerCanChoose(),
                        requirement.isCombineResources(),
                        resources.stream()
                                .filter(resource -> resource.getResourceTypeId().equals(requirement.getResourceTypeId()))
                                .filter(resource -> {
                                    List<Long> allowed = restrictions.get(resource.getId());
                                    return allowed == null || allowed.contains(service.getId());
                                })
                                .map(Resource::getId)
                                .toList()))
                .toList();

        ServiceSpec spec = new ServiceSpec(service.duration(), service.bufferBefore(), service.bufferAfter(),
                requirementSpecs);

        // Widen the query window by the longest a single reservation can span,
        // so one starting the previous evening is still seen as a conflict.
        Instant windowStart = from.atStartOfDay(zone).toInstant().minus(spec.maxSpan());
        Instant windowEnd = to.plusDays(1).atStartOfDay(zone).toInstant().plus(spec.maxSpan());

        List<TimeRange> businessBlocks = new ArrayList<>();
        Map<Long, List<TimeRange>> busy = new HashMap<>();
        Map<Long, List<SeatUse>> seatUse = new HashMap<>();
        for (BlockedTime blocked : blockedTimeService.overlapping(businessId, windowStart, windowEnd)) {
            if (blocked.isBusinessWide()) {
                businessBlocks.add(blocked.range());
            } else {
                busy.computeIfAbsent(blocked.getResourceId(), key -> new ArrayList<>()).add(blocked.range());
            }
        }
        for (OccupiedInterval occupied : bookedIntervalProvider.occupiedIntervals(
                businessId, windowStart, windowEnd, excludeReservationId)) {
            if (occupied.seats() == null) {
                busy.computeIfAbsent(occupied.resourceId(), key -> new ArrayList<>()).add(occupied.range());
            } else {
                seatUse.computeIfAbsent(occupied.resourceId(), key -> new ArrayList<>())
                        .add(new SeatUse(occupied.range(), occupied.seats()));
            }
        }

        AvailabilityContext context = new AvailabilityContext(
                zone,
                spec,
                participants,
                customerFacing ? ReservationRules.customerFacing(settings) : ReservationRules.internal(settings),
                openingHoursService.intervalsOf(businessId),
                businessBlocks,
                resources.stream().collect(Collectors.toMap(Resource::getId,
                        resource -> new ResourceSpec(resource.getId(), resource.getResourceTypeId(),
                                resource.getName(), resource.getCapacity(), resource.isSharedSeating(),
                                resource.getMinPartySize(),
                                areas.containsKey(resource.getId()) ? areas.get(resource.getId()).id() : null))),
                resourceService.hoursOf(businessId, resourceIds),
                busy,
                seatUse,
                clock.instant());
        return new Loaded(context, resources.stream().collect(Collectors.toMap(Resource::getId, Function.identity())),
                areas);
    }

    // --- mapping -----------------------------------------------------------

    private static DaySlotsResponse toResponse(DayAvailability day, Loaded loaded, boolean customerFacing) {
        return new DaySlotsResponse(day.date(),
                day.slots().stream().map(slot -> toSlot(slot, loaded, customerFacing)).toList());
    }

    private static SlotResponse toSlot(AvailableSlot slot, Loaded loaded, boolean customerFacing) {
        return new SlotResponse(slot.time(), slot.startTime(), slot.endTime(),
                slot.options().stream().map(option -> toRequirement(option, loaded, customerFacing)).toList());
    }

    private static SlotRequirement toRequirement(RequirementOption option, Loaded loaded, boolean customerFacing) {
        RequirementSpec requirement = option.requirement();
        // Customers only see concrete resources they are allowed to pick.
        boolean listed = !customerFacing || requirement.customerChoosable();
        List<ResourceOption> free = listed
                ? option.candidateIds().stream().map(id -> resourceOption(id, option, loaded)).toList()
                : List.of();
        List<AllocationOption> allocations = listed
                ? option.allocations().stream().map(allocation -> allocationOption(allocation, option, loaded)).toList()
                : List.of();
        return new SlotRequirement(requirement.requirementId(), requirement.resourceTypeId(),
                requirement.resourceTypeName(), requirement.quantity(), requirement.required(),
                requirement.customerChoosable(), requirement.combinable(), free, allocations);
    }

    private static ResourceOption resourceOption(Long id, RequirementOption option, Loaded loaded) {
        Resource resource = loaded.resources().get(id);
        return new ResourceOption(resource.getId(), resource.getName(), resource.getCapacity(),
                option.seatsLeft().get(resource.getId()));
    }

    private static AllocationOption allocationOption(AllocationFinder.Allocation allocation, RequirementOption option,
                                                     Loaded loaded) {
        Area area = loaded.areas().get(allocation.resources().get(0).id());
        Integer capacity = allocation.capacity() >= Integer.MAX_VALUE ? null : (int) allocation.capacity();
        return new AllocationOption(
                allocation.resourceIds().stream().map(id -> resourceOption(id, option, loaded)).toList(),
                capacity, area == null ? null : area.name());
    }
}
