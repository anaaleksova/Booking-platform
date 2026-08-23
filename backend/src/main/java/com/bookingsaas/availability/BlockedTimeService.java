package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.BlockedTimeRequest;
import com.bookingsaas.availability.dto.BlockedTimeResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import com.bookingsaas.staff.Staff;
import com.bookingsaas.staff.StaffService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Creating and listing the one-off blocks that remove availability. */
@Service
public class BlockedTimeService {

    private final BlockedTimeRepository repository;
    private final BusinessService businessService;
    private final StaffService staffService;
    private final ResourceService resourceService;
    private final CurrentTenant currentTenant;

    public BlockedTimeService(BlockedTimeRepository repository,
                              BusinessService businessService,
                              StaffService staffService,
                              ResourceService resourceService,
                              CurrentTenant currentTenant) {
        this.repository = repository;
        this.businessService = businessService;
        this.staffService = staffService;
        this.resourceService = resourceService;
        this.currentTenant = currentTenant;
    }

    @Transactional(readOnly = true)
    public List<BlockedTime> overlapping(Long businessId, Instant from, Instant to) {
        return repository.findOverlapping(businessId, from, to);
    }

    @Transactional(readOnly = true)
    public List<BlockedTimeResponse> list(LocalDate from, LocalDate to) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();

        LocalDate start = from != null ? from : LocalDate.now(zone);
        LocalDate end = to != null ? to : start.plusMonths(3);
        List<BlockedTime> blocks = repository.findOverlapping(businessId,
                start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant());

        return blocks.stream().map(block -> BlockedTimeResponse.from(block, zone,
                        block.getStaffId() == null ? null
                                : staffService.requireForBusiness(businessId, block.getStaffId()).getName(),
                        block.getResourceId() == null ? null
                                : resourceService.requireForBusiness(businessId, block.getResourceId()).getName()))
                .toList();
    }

    @Transactional
    public BlockedTimeResponse create(BlockedTimeRequest request) {
        Long businessId = currentTenant.businessId();
        Business business = businessService.requireById(businessId);
        ZoneId zone = business.zoneId();

        if (!request.startDateTime().isBefore(request.endDateTime())) {
            throw ApiException.validation("The block must start before it ends.");
        }

        // Ownership is checked through tenant-scoped lookups, so a foreign
        // staff or resource id fails as "not found" rather than being stored.
        String staffName = null;
        if (request.staffId() != null) {
            Staff staff = staffService.requireForBusiness(businessId, request.staffId());
            staffName = staff.getName();
            requireStaffPermission(staff);
        } else {
            // Business-wide and resource blocks are an owner-level action.
            currentTenant.requireOwner();
        }
        String resourceName = null;
        if (request.resourceId() != null) {
            Resource resource = resourceService.requireForBusiness(businessId, request.resourceId());
            resourceName = resource.getName();
        }

        BlockedTime blocked = new BlockedTime();
        blocked.setBusinessId(businessId);
        blocked.setStaffId(request.staffId());
        blocked.setResourceId(request.resourceId());
        blocked.setStartDateTime(request.startDateTime().atZone(zone).toInstant());
        blocked.setEndDateTime(request.endDateTime().atZone(zone).toInstant());
        blocked.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());

        return BlockedTimeResponse.from(repository.save(blocked), zone, staffName, resourceName);
    }

    @Transactional
    public void delete(Long id) {
        Long businessId = currentTenant.businessId();
        BlockedTime blocked = repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Blocked time", id));
        if (blocked.getStaffId() != null) {
            requireStaffPermission(staffService.requireForBusiness(businessId, blocked.getStaffId()));
        } else {
            currentTenant.requireOwner();
        }
        repository.delete(blocked);
    }

    /** A staff member may block only their own calendar; an owner, anyone's. */
    private void requireStaffPermission(Staff staff) {
        if (currentTenant.isOwner()) {
            return;
        }
        boolean ownCalendar = staff.getUserId() != null && staff.getUserId().equals(currentTenant.userId());
        if (!ownCalendar) {
            throw ApiException.accessDenied("You can only block time on your own calendar.");
        }
    }
}
