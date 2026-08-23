package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.DayAvailability;
import com.bookingsaas.availability.spi.BookedIntervalProvider;
import com.bookingsaas.availability.spi.OccupiedInterval;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.business.WorkingHoursService;
import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.domain.TimeRange;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import com.bookingsaas.staff.Staff;
import com.bookingsaas.staff.StaffService;
import com.bookingsaas.staff.StaffWorkingHour;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads everything the {@link AvailabilityEngine} needs and hands back slots.
 *
 * <p>Deliberately usable from anywhere: the public booking page, the dashboard
 * and the booking transaction all call the same methods, so no caller can end
 * up with its own idea of what is free.
 */
@Service
public class AvailabilityService {

    private final AvailabilityEngine engine;
    private final BusinessService businessService;
    private final ServiceCatalogService serviceCatalog;
    private final StaffService staffService;
    private final ResourceService resourceService;
    private final WorkingHoursService workingHoursService;
    private final BlockedTimeService blockedTimeService;
    private final BookedIntervalProvider bookedIntervalProvider;
    private final Clock clock;

    public AvailabilityService(AvailabilityEngine engine,
                               BusinessService businessService,
                               ServiceCatalogService serviceCatalog,
                               StaffService staffService,
                               ResourceService resourceService,
                               WorkingHoursService workingHoursService,
                               BlockedTimeService blockedTimeService,
                               BookedIntervalProvider bookedIntervalProvider,
                               Clock clock) {
        this.engine = engine;
        this.businessService = businessService;
        this.serviceCatalog = serviceCatalog;
        this.staffService = staffService;
        this.resourceService = resourceService;
        this.workingHoursService = workingHoursService;
        this.blockedTimeService = blockedTimeService;
        this.bookedIntervalProvider = bookedIntervalProvider;
        this.clock = clock;
    }

    // --- slot search -------------------------------------------------------

    /**
     * @param enforceLeadTimes true for customer-facing searches; false for the
     *                         dashboard, where the business may book a slot
     *                         starting in ten minutes
     */
    @Transactional(readOnly = true)
    public List<DayAvailability> search(Long businessId, AvailabilityQuery query, boolean enforceLeadTimes) {
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
        AvailabilityContext context = buildContext(businessId, service, query, enforceLeadTimes, from, to);
        return engine.availableSlots(context, from, to);
    }

    // --- single slot, used by booking --------------------------------------

    /**
     * Validates one exact start time and picks the staff member and resource to
     * use. Called inside the booking transaction — never from a controller —
     * so the answer cannot go stale between check and insert.
     */
    @Transactional(readOnly = true)
    public SlotAssignment resolve(Long businessId,
                                  BookableService service,
                                  Instant start,
                                  Long requestedStaffId,
                                  Long requestedResourceId,
                                  Long excludeBookingId,
                                  boolean enforceLeadTimes) {
        ZoneId zone = businessService.requireById(businessId).zoneId();
        LocalDate date = LocalDate.ofInstant(start, zone);
        AvailabilityQuery query = new AvailabilityQuery(service.getId(), date, date,
                requestedStaffId, requestedResourceId, excludeBookingId);

        AvailabilityContext context = buildContext(businessId, service, query, enforceLeadTimes, date, date);
        SlotEvaluation evaluation = engine.evaluate(context, start);
        if (!evaluation.available()) {
            throw evaluation.toException();
        }

        Long staffId = service.isRequiresStaff() || requestedStaffId != null
                ? pickLeastBusy(evaluation.staffIds(), context.staffBusy())
                : null;
        Long resourceId = service.isRequiresResource() || requestedResourceId != null
                ? pickLeastBusy(evaluation.resourceIds(), context.resourceBusy())
                : null;

        if (service.isRequiresStaff() && staffId == null) {
            throw ApiException.of(ErrorCode.NO_STAFF_AVAILABLE, "No staff member is available at that time.");
        }
        if (service.isRequiresResource() && resourceId == null) {
            throw ApiException.of(ErrorCode.NO_RESOURCE_AVAILABLE, "No resource is available at that time.");
        }

        TimeRange appointment = ServiceSpec.of(service).appointmentRange(start);
        TimeRange block = ServiceSpec.of(service).blockRange(start);
        return new SlotAssignment(staffId, resourceId, appointment.start(), appointment.end(),
                block.start(), block.end());
    }

    /**
     * Spreads work across the team instead of always filling the first name in
     * the list. Ties keep the incoming (alphabetical) order, so the choice is
     * deterministic and tests stay readable.
     */
    private static Long pickLeastBusy(List<Long> candidates, Map<Long, List<TimeRange>> busy) {
        return candidates.stream()
                .min(Comparator.comparingInt(id -> busy.getOrDefault(id, List.of()).size()))
                .orElse(null);
    }

    // --- context assembly --------------------------------------------------

    private AvailabilityContext buildContext(Long businessId,
                                             BookableService service,
                                             AvailabilityQuery query,
                                             boolean enforceLeadTimes,
                                             LocalDate from,
                                             LocalDate to) {
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();
        BusinessSettings settings = businessService.settingsOf(businessId);
        ServiceSpec spec = ServiceSpec.of(service);

        // Widen the query window by the longest a single booking can span, so a
        // booking starting the previous evening is still seen as a conflict.
        Instant windowStart = from.atStartOfDay(zone).toInstant().minus(spec.maxSpan());
        Instant windowEnd = to.plusDays(1).atStartOfDay(zone).toInstant().plus(spec.maxSpan());

        List<Staff> staff = eligibleStaff(businessId, service, query.staffId());
        List<Resource> resources = eligibleResources(businessId, service, query.resourceId());
        List<Long> staffIds = staff.stream().map(Staff::getId).toList();
        List<Long> resourceIds = resources.stream().map(Resource::getId).toList();

        Map<Long, List<DayInterval>> staffHours = new HashMap<>();
        for (StaffWorkingHour hour : staffService.workingHoursOf(businessId, staffIds)) {
            staffHours.computeIfAbsent(hour.getStaffId(), key -> new ArrayList<>())
                    .add(new DayInterval((int) hour.getDayOfWeek(), hour.getStartTime(), hour.getEndTime()));
        }

        List<TimeRange> businessBlocks = new ArrayList<>();
        Map<Long, List<TimeRange>> staffBusy = new HashMap<>();
        Map<Long, List<TimeRange>> resourceBusy = new HashMap<>();

        for (BlockedTime blocked : blockedTimeService.overlapping(businessId, windowStart, windowEnd)) {
            TimeRange range = blocked.range();
            if (blocked.isBusinessWide()) {
                businessBlocks.add(range);
                continue;
            }
            if (blocked.getStaffId() != null) {
                staffBusy.computeIfAbsent(blocked.getStaffId(), key -> new ArrayList<>()).add(range);
            }
            if (blocked.getResourceId() != null) {
                resourceBusy.computeIfAbsent(blocked.getResourceId(), key -> new ArrayList<>()).add(range);
            }
        }

        for (OccupiedInterval occupied : bookedIntervalProvider.occupiedIntervals(
                businessId, windowStart, windowEnd, query.excludeBookingId())) {
            if (occupied.staffId() != null) {
                staffBusy.computeIfAbsent(occupied.staffId(), key -> new ArrayList<>()).add(occupied.range());
            }
            if (occupied.resourceId() != null) {
                resourceBusy.computeIfAbsent(occupied.resourceId(), key -> new ArrayList<>()).add(occupied.range());
            }
        }

        List<DayInterval> businessHours = workingHoursService.intervalsOf(businessId);

        return new AvailabilityContext(
                zone,
                spec,
                enforceLeadTimes ? BookingRules.publicBooking(settings) : BookingRules.internalBooking(settings),
                businessHours,
                businessBlocks,
                staffIds,
                staffHours,
                staffBusy,
                resourceIds,
                resourceBusy,
                clock.instant());
    }

    private List<Staff> eligibleStaff(Long businessId, BookableService service, Long requestedStaffId) {
        List<Staff> eligible = staffService.eligibleStaffFor(businessId, service.getId());
        if (requestedStaffId == null) {
            return eligible;
        }
        return eligible.stream()
                .filter(member -> member.getId().equals(requestedStaffId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> ApiException.of(ErrorCode.STAFF_NOT_ELIGIBLE,
                        "That staff member does not offer this service."));
    }

    private List<Resource> eligibleResources(Long businessId, BookableService service, Long requestedResourceId) {
        if (!service.isRequiresResource() && requestedResourceId == null) {
            return List.of();
        }
        List<Resource> eligible = resourceService.eligibleResourcesFor(businessId, service.getId());
        if (requestedResourceId == null) {
            return eligible;
        }
        return eligible.stream()
                .filter(resource -> resource.getId().equals(requestedResourceId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_ELIGIBLE,
                        "That resource cannot host this service."));
    }
}
