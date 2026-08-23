package com.bookingsaas.staff;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.service.BookableServiceRepository;
import com.bookingsaas.staff.dto.StaffRequest;
import com.bookingsaas.staff.dto.StaffResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Staff records, their service assignments and their weekly schedules. */
@Service
public class StaffService {

    private final StaffRepository staffRepository;
    private final StaffServiceLinkRepository linkRepository;
    private final StaffWorkingHourRepository workingHourRepository;
    private final BookableServiceRepository serviceRepository;
    private final CurrentTenant currentTenant;

    public StaffService(StaffRepository staffRepository,
                        StaffServiceLinkRepository linkRepository,
                        StaffWorkingHourRepository workingHourRepository,
                        BookableServiceRepository serviceRepository,
                        CurrentTenant currentTenant) {
        this.staffRepository = staffRepository;
        this.linkRepository = linkRepository;
        this.workingHourRepository = workingHourRepository;
        this.serviceRepository = serviceRepository;
        this.currentTenant = currentTenant;
    }

    // --- cross-module lookups ---------------------------------------------

    @Transactional(readOnly = true)
    public Staff requireForBusiness(Long businessId, Long staffId) {
        return staffRepository.findByIdAndBusinessId(staffId, businessId)
                .orElseThrow(() -> ApiException.notFound("Staff member", staffId));
    }

    /**
     * Staff who may deliver a service: active, and either explicitly assigned
     * to it or — when nobody has been assigned at all — anyone, so an
     * unconfigured business is still bookable.
     */
    @Transactional(readOnly = true)
    public List<Staff> eligibleStaffFor(Long businessId, Long serviceId) {
        List<Long> assigned = linkRepository.findStaffIdsByServiceId(serviceId);
        if (assigned.isEmpty()) {
            return staffRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId);
        }
        return staffRepository.findByBusinessIdAndIdInOrderByNameAsc(businessId, assigned).stream()
                .filter(Staff::isActive)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffWorkingHour> workingHoursOf(Long businessId, Collection<Long> staffIds) {
        return staffIds.isEmpty() ? List.of() : workingHourRepository.findByBusinessIdAndStaffIdIn(businessId, staffIds);
    }

    /** The staff record behind a logged-in STAFF user, if there is one. */
    @Transactional(readOnly = true)
    public Optional<Staff> staffRecordOf(Long businessId, Long userId) {
        return staffRepository.findByBusinessIdAndUserId(businessId, userId);
    }

    // --- dashboard API -----------------------------------------------------

    @Transactional(readOnly = true)
    public List<StaffResponse> list(boolean activeOnly) {
        Long businessId = currentTenant.businessId();
        List<Staff> staff = activeOnly
                ? staffRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId)
                : staffRepository.findByBusinessIdOrderByNameAsc(businessId);
        if (staff.isEmpty()) {
            return List.of();
        }

        List<Long> ids = staff.stream().map(Staff::getId).toList();
        Map<Long, List<Long>> servicesByStaff = linkRepository.findByStaffIdIn(ids).stream()
                .collect(Collectors.groupingBy(StaffServiceLink::getStaffId,
                        Collectors.mapping(StaffServiceLink::getServiceId, Collectors.toList())));
        Map<Long, List<DayInterval>> hoursByStaff = workingHourRepository
                .findByBusinessIdAndStaffIdIn(businessId, ids).stream()
                .collect(Collectors.groupingBy(StaffWorkingHour::getStaffId,
                        Collectors.mapping(StaffService::toInterval, Collectors.toList())));

        return staff.stream()
                .map(member -> StaffResponse.from(member,
                        servicesByStaff.getOrDefault(member.getId(), List.of()),
                        sorted(hoursByStaff.getOrDefault(member.getId(), List.of()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffResponse get(Long id) {
        Long businessId = currentTenant.businessId();
        Staff staff = requireForBusiness(businessId, id);
        return StaffResponse.from(staff, serviceIdsOf(id), workingHoursDto(id));
    }

    @Transactional
    public StaffResponse create(StaffRequest request) {
        currentTenant.requireOwner();
        Staff staff = new Staff();
        staff.setBusinessId(currentTenant.businessId());
        apply(staff, request);
        Staff saved = staffRepository.save(staff);
        replaceServices(saved, request.serviceIds());
        return StaffResponse.from(saved, serviceIdsOf(saved.getId()), workingHoursDto(saved.getId()));
    }

    @Transactional
    public StaffResponse update(Long id, StaffRequest request) {
        currentTenant.requireOwner();
        Staff staff = requireForBusiness(currentTenant.businessId(), id);
        apply(staff, request);
        Staff saved = staffRepository.save(staff);
        if (request.serviceIds() != null) {
            replaceServices(saved, request.serviceIds());
        }
        return StaffResponse.from(saved, serviceIdsOf(id), workingHoursDto(id));
    }

    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        Staff staff = requireForBusiness(currentTenant.businessId(), id);
        linkRepository.deleteByStaffId(id);
        workingHourRepository.deleteByStaffId(id);
        try {
            staffRepository.delete(staff);
            staffRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // bookings reference staff with ON DELETE RESTRICT: history stays intact
            throw ApiException.of(ErrorCode.STAFF_IN_USE,
                    "This staff member has bookings and cannot be deleted. Deactivate them instead.");
        }
    }

    @Transactional
    public StaffResponse replaceServices(Long staffId, List<Long> serviceIds) {
        currentTenant.requireOwner();
        Staff staff = requireForBusiness(currentTenant.businessId(), staffId);
        replaceServices(staff, serviceIds == null ? List.of() : serviceIds);
        return StaffResponse.from(staff, serviceIdsOf(staffId), workingHoursDto(staffId));
    }

    /** Replaces a staff member's whole week; an empty list means "follow business hours". */
    @Transactional
    public List<DayInterval> replaceWorkingHours(Long staffId, List<DayInterval> intervals) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        requireForBusiness(businessId, staffId);
        DayInterval.validate(intervals);

        workingHourRepository.deleteByStaffId(staffId);
        workingHourRepository.flush();
        if (intervals != null && !intervals.isEmpty()) {
            workingHourRepository.saveAll(intervals.stream()
                    .map(interval -> new StaffWorkingHour(businessId, staffId, interval.dayOfWeek(),
                            interval.startTime(), interval.endTime()))
                    .toList());
        }
        return workingHoursDto(staffId);
    }

    @Transactional(readOnly = true)
    public List<DayInterval> workingHours(Long staffId) {
        requireForBusiness(currentTenant.businessId(), staffId);
        return workingHoursDto(staffId);
    }

    // --- internals ---------------------------------------------------------

    private void apply(Staff staff, StaffRequest request) {
        staff.setName(request.name().trim());
        staff.setEmail(blankToNull(request.email()));
        staff.setPhone(blankToNull(request.phone()));
        staff.setPhotoUrl(blankToNull(request.photoUrl()));
        staff.setActive(request.active());
    }

    private void replaceServices(Staff staff, List<Long> serviceIds) {
        linkRepository.deleteByStaffId(staff.getId());
        linkRepository.flush();
        if (serviceIds == null || serviceIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = serviceIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        // Cross-tenant guard: ids that are not this business's services simply
        // do not come back from the scoped query, so they can never be linked.
        Set<Long> owned = serviceRepository.findByBusinessIdAndIdIn(staff.getBusinessId(), List.copyOf(distinct))
                .stream()
                .map(com.bookingsaas.service.BookableService::getId)
                .collect(Collectors.toSet());
        if (owned.size() != distinct.size()) {
            throw ApiException.validation("One or more selected services do not belong to this business.");
        }
        linkRepository.saveAll(owned.stream().map(id -> new StaffServiceLink(staff.getId(), id)).toList());
    }

    private List<Long> serviceIdsOf(Long staffId) {
        return linkRepository.findByStaffId(staffId).stream().map(StaffServiceLink::getServiceId).toList();
    }

    private List<DayInterval> workingHoursDto(Long staffId) {
        return workingHourRepository.findByStaffIdOrderByDayOfWeekAscStartTimeAsc(staffId).stream()
                .map(StaffService::toInterval)
                .toList();
    }

    private static List<DayInterval> sorted(List<DayInterval> intervals) {
        return intervals.stream()
                .sorted(Comparator.comparing(DayInterval::dayOfWeek).thenComparing(DayInterval::startTime))
                .toList();
    }

    static DayInterval toInterval(StaffWorkingHour hour) {
        return new DayInterval((int) hour.getDayOfWeek(), hour.getStartTime(), hour.getEndTime());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
