package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.BlockedTimeRequest;
import com.bookingsaas.availability.dto.BlockedTimeResponse;
import com.bookingsaas.availability.spi.ScheduleConflict;
import com.bookingsaas.availability.spi.ScheduleConflictProvider;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.employee.Employee;
import com.bookingsaas.employee.EmployeeService;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creating and listing the one-off blocks that remove availability.
 *
 * <p>A block never moves bookings by itself: it reports the ones it collides
 * with, and the owner decides per booking whether to move or cancel it. Until
 * then the block still keeps <em>new</em> bookings out.
 *
 * <p>Staff manage time off on their own calendar only, and see the business's
 * closures without being able to change them.
 */
@Service
public class BlockedTimeService {

    private final BlockedTimeRepository repository;
    private final BusinessService businessService;
    private final ResourceService resourceService;
    private final EmployeeService employeeService;
    private final ScheduleConflictProvider conflictProvider;
    private final ApplicationEventPublisher events;
    private final CurrentTenant currentTenant;

    public BlockedTimeService(BlockedTimeRepository repository,
                              BusinessService businessService,
                              ResourceService resourceService,
                              EmployeeService employeeService,
                              ScheduleConflictProvider conflictProvider,
                              ApplicationEventPublisher events,
                              CurrentTenant currentTenant) {
        this.repository = repository;
        this.businessService = businessService;
        this.resourceService = resourceService;
        this.employeeService = employeeService;
        this.conflictProvider = conflictProvider;
        this.events = events;
        this.currentTenant = currentTenant;
    }

    @Transactional(readOnly = true)
    public List<BlockedTime> overlapping(Long businessId, Instant from, Instant to) {
        return repository.findOverlapping(businessId, from, to);
    }

    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> list(LocalDate from, LocalDate to) {
        Long businessId = currentTenant.businessId();
        ZoneId zone = businessService.zoneOf(businessId);
        boolean owner = currentTenant.isOwner();
        Long ownResourceId = owner ? null : ownResourceId(businessId).orElse(null);

        LocalDate start = from != null ? from : LocalDate.now(zone);
        LocalDate end = to != null ? to : start.plusMonths(3);
        List<BlockedTime> blocks = repository.findOverlapping(businessId,
                        start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant()).stream()
                // Staff see the closures and their own time off, nobody else's.
                .filter(block -> owner || block.isBusinessWide() || block.getResourceId().equals(ownResourceId))
                .toList();

        Map<Long, Resource> resources = resourceService.byIds(businessId, blocks.stream()
                        .map(BlockedTime::getResourceId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Resource::getId, Function.identity()));
        Map<Long, Employee> employeesByResource = employeeService.employeesOf(businessId).stream()
                .collect(Collectors.toMap(Employee::getResourceId, Function.identity()));
        return blocks.stream()
                .map(block -> BlockedTimeResponse.from(block, zone,
                        block.getResourceId() == null ? null : resources.get(block.getResourceId()).getName(),
                        enteredByStaff(block, employeesByResource.get(block.getResourceId())),
                        // A closure hits everyone's bookings, which are the owner's to see.
                        owner || !block.isBusinessWide() ? conflictsOf(block) : List.of()))
                .toList();
    }

    /** The bookings a block would collide with, before it is saved. */
    @Transactional(readOnly = true)
    public List<ScheduleConflict> previewConflicts(Long resourceId, LocalDateTime startTime, LocalDateTime endTime) {
        Long businessId = currentTenant.businessId();
        ZoneId zone = businessService.zoneOf(businessId);
        if (!startTime.isBefore(endTime)) {
            throw ApiException.validation("The block must start before it ends.");
        }
        BlockedTime draft = new BlockedTime();
        draft.setBusinessId(businessId);
        if (resourceId != null) {
            draft.setResourceId(resourceService.requireForBusiness(businessId, resourceId).getId());
            requirePermission(businessId, resourceId);
        } else {
            currentTenant.requireOwner();
        }
        draft.setStartTime(startTime.atZone(zone).toInstant());
        draft.setEndTime(endTime.atZone(zone).toInstant());
        return conflictsOf(draft);
    }

    @Transactional
    public BlockedTimeResponse create(BlockedTimeRequest request) {
        Long businessId = currentTenant.businessId();
        ZoneId zone = businessService.zoneOf(businessId);

        if (!request.startTime().isBefore(request.endTime())) {
            throw ApiException.validation("The block must start before it ends.");
        }

        // Ownership is checked through a tenant-scoped lookup, so a foreign
        // resource id fails as "not found" rather than being stored.
        String resourceName = null;
        if (request.resourceId() != null) {
            Resource resource = resourceService.requireForBusiness(businessId, request.resourceId());
            resourceName = resource.getName();
            requirePermission(businessId, resource.getId());
        } else {
            currentTenant.requireOwner();                // closing the business is an owner action
        }

        BlockedTime blocked = new BlockedTime();
        blocked.setBusinessId(businessId);
        blocked.setResourceId(request.resourceId());
        blocked.setStartTime(request.startTime().atZone(zone).toInstant());
        blocked.setEndTime(request.endTime().atZone(zone).toInstant());
        blocked.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        blocked.setCreatedByUserId(currentTenant.userId());
        BlockedTime saved = repository.save(blocked);

        List<ScheduleConflict> conflicts = conflictsOf(saved);
        boolean byStaff = !currentTenant.isOwner();
        if (byStaff) {
            events.publishEvent(new StaffTimeOffEntered(businessId, businessService.requireById(businessId).getName(),
                    resourceName, request.startTime(), request.endTime(), saved.getReason(), conflicts));
        }
        return BlockedTimeResponse.from(saved, zone, resourceName, byStaff, conflicts);
    }

    @Transactional
    public void delete(Long id) {
        Long businessId = currentTenant.businessId();
        BlockedTime blocked = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Blocked time", id));
        if (blocked.getResourceId() != null) {
            requirePermission(businessId, blocked.getResourceId());
        } else {
            currentTenant.requireOwner();
        }
        repository.delete(blocked);
    }

    /**
     * What the block collides with, by the engine's own rules: a resource's
     * block takes the resource away for the whole booking including buffers; a
     * closure removes the opening hours an appointment needs.
     */
    private List<ScheduleConflict> conflictsOf(BlockedTime block) {
        return block.isBusinessWide()
                ? conflictProvider.during(block.getBusinessId(), block.getStartTime(), block.getEndTime())
                : conflictProvider.holding(block.getBusinessId(), block.getResourceId(), block.getStartTime(),
                        block.getEndTime());
    }

    private static boolean enteredByStaff(BlockedTime block, Employee employee) {
        return employee != null && employee.getUserId() != null
                && employee.getUserId().equals(block.getCreatedByUserId());
    }

    private Optional<Long> ownResourceId(Long businessId) {
        return employeeService.employeeOf(businessId, currentTenant.userId()).map(Employee::getResourceId);
    }

    /** An owner may block any resource; a staff member only their own calendar. */
    private void requirePermission(Long businessId, Long resourceId) {
        if (currentTenant.isOwner()) {
            return;
        }
        if (!ownResourceId(businessId).filter(resourceId::equals).isPresent()) {
            throw ApiException.accessDenied("You can only block time on your own calendar.");
        }
    }
}
