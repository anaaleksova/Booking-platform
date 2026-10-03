package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.BlockedTimeRequest;
import com.bookingsaas.availability.dto.BlockedTimeResponse;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.employee.Employee;
import com.bookingsaas.employee.EmployeeService;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Creating and listing the one-off blocks that remove availability. */
@Service
public class BlockedTimeService {

    private final BlockedTimeRepository repository;
    private final BusinessService businessService;
    private final ResourceService resourceService;
    private final EmployeeService employeeService;
    private final CurrentTenant currentTenant;

    public BlockedTimeService(BlockedTimeRepository repository,
                              BusinessService businessService,
                              ResourceService resourceService,
                              EmployeeService employeeService,
                              CurrentTenant currentTenant) {
        this.repository = repository;
        this.businessService = businessService;
        this.resourceService = resourceService;
        this.employeeService = employeeService;
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

        LocalDate start = from != null ? from : LocalDate.now(zone);
        LocalDate end = to != null ? to : start.plusMonths(3);
        List<BlockedTime> blocks = repository.findOverlapping(businessId,
                start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant());

        Map<Long, Resource> resources = resourceService.byIds(businessId, blocks.stream()
                        .map(BlockedTime::getResourceId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Resource::getId, Function.identity()));
        return blocks.stream()
                .map(block -> BlockedTimeResponse.from(block, zone,
                        block.getResourceId() == null ? null : resources.get(block.getResourceId()).getName()))
                .toList();
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

        return BlockedTimeResponse.from(repository.save(blocked), zone, resourceName);
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

    /** An owner may block any resource; a staff member only their own calendar. */
    private void requirePermission(Long businessId, Long resourceId) {
        if (currentTenant.isOwner()) {
            return;
        }
        boolean ownCalendar = employeeService.employeeOf(businessId, currentTenant.userId())
                .map(Employee::getResourceId)
                .filter(resourceId::equals)
                .isPresent();
        if (!ownCalendar) {
            throw ApiException.accessDenied("You can only block time on your own calendar.");
        }
    }
}
