package com.bookingsaas.employee;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.employee.dto.EmployeeRequest;
import com.bookingsaas.employee.dto.EmployeeResponse;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employees, kept in lock-step with the resource that represents each of them
 * in the availability system.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository repository;
    private final ResourceService resourceService;
    private final CurrentTenant currentTenant;

    public EmployeeService(EmployeeRepository repository,
                           ResourceService resourceService,
                           CurrentTenant currentTenant) {
        this.repository = repository;
        this.resourceService = resourceService;
        this.currentTenant = currentTenant;
    }

    // --- cross-module lookups ---------------------------------------------

    @Transactional(readOnly = true)
    public Employee requireForBusiness(Long businessId, Long employeeId) {
        return repository.findByIdAndBusinessId(employeeId, businessId)
                .orElseThrow(() -> ApiException.notFound("Employee", employeeId));
    }

    /** The employee behind a logged-in STAFF user, if there is one. */
    @Transactional(readOnly = true)
    public Optional<Employee> employeeOf(Long businessId, Long userId) {
        return repository.findByBusinessIdAndUserId(businessId, userId);
    }

    // --- admin API ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EmployeeResponse> list() {
        Long businessId = currentTenant.businessId();
        List<Employee> employees = repository.findByBusinessIdOrderByNameAsc(businessId);
        if (employees.isEmpty()) {
            return List.of();
        }
        List<Long> resourceIds = employees.stream().map(Employee::getResourceId).toList();
        Map<Long, Resource> resources = resourceService.byIds(businessId, resourceIds).stream()
                .collect(Collectors.toMap(Resource::getId, Function.identity()));
        Map<Long, List<Long>> services = resourceService.serviceRestrictions(resourceIds);
        Map<Long, List<DayInterval>> hours = resourceService.hoursOf(businessId, resourceIds);

        return employees.stream()
                .map(employee -> {
                    Resource resource = resources.get(employee.getResourceId());
                    return EmployeeResponse.from(employee, resource == null ? null : resource.getCapacity(),
                            resource == null ? null : resource.getImageMediaId(),
                            services.getOrDefault(employee.getResourceId(), List.of()),
                            sorted(hours.getOrDefault(employee.getResourceId(), List.of())));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        Long businessId = currentTenant.businessId();
        return toResponse(businessId, requireForBusiness(businessId, id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        String name = request.name().trim();

        Resource resource = resourceService.createManaged(businessId, name, capacityOf(request), request.active(),
                request.imageMediaId());
        Employee employee = new Employee();
        employee.setBusinessId(businessId);
        employee.setResourceId(resource.getId());
        apply(employee, request);
        Employee saved = repository.save(employee);
        resourceService.replaceServicesOf(businessId, resource.getId(), request.serviceIds());
        return toResponse(businessId, saved);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Employee employee = requireForBusiness(businessId, id);
        apply(employee, request);
        Employee saved = repository.save(employee);
        resourceService.syncManaged(businessId, employee.getResourceId(), saved.getName(), capacityOf(request),
                saved.isActive(), request.imageMediaId());
        if (request.serviceIds() != null) {
            resourceService.replaceServicesOf(businessId, employee.getResourceId(), request.serviceIds());
        }
        return toResponse(businessId, saved);
    }

    @Transactional
    public EmployeeResponse setActive(Long id, boolean active) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Employee employee = requireForBusiness(businessId, id);
        employee.setActive(active);
        Employee saved = repository.save(employee);
        Resource resource = resourceService.requireForBusiness(businessId, employee.getResourceId());
        resourceService.syncManaged(businessId, resource.getId(), saved.getName(), resource.getCapacity(), active,
                resource.getImageMediaId());
        return toResponse(businessId, saved);
    }

    /** Replaces the employee's week; an empty list means "works whenever the business is open". */
    @Transactional
    public List<DayInterval> replaceWorkingHours(Long id, List<DayInterval> intervals) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Employee employee = requireForBusiness(businessId, id);
        return resourceService.replaceHoursOf(businessId, employee.getResourceId(), intervals);
    }

    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Employee employee = requireForBusiness(businessId, id);
        try {
            repository.delete(employee);
            repository.flush();
            resourceService.deleteManaged(businessId, employee.getResourceId());
        } catch (DataIntegrityViolationException ex) {
            // Reservations reference the employee's resource with ON DELETE RESTRICT.
            throw ApiException.of(ErrorCode.EMPLOYEE_IN_USE,
                    "This employee has reservations and cannot be deleted. Deactivate them instead.");
        }
    }

    // --- internals ---------------------------------------------------------

    private EmployeeResponse toResponse(Long businessId, Employee employee) {
        Resource resource = resourceService.requireForBusiness(businessId, employee.getResourceId());
        return EmployeeResponse.from(employee, resource.getCapacity(), resource.getImageMediaId(),
                resourceService.serviceIdsOf(resource.getId()), resourceService.hoursDto(resource.getId()));
    }

    private static Integer capacityOf(EmployeeRequest request) {
        return request.capacity() == null ? 1 : request.capacity();
    }

    private static void apply(Employee employee, EmployeeRequest request) {
        employee.setName(request.name().trim());
        employee.setEmail(blankToNull(request.email()));
        employee.setPhone(blankToNull(request.phone()));
        employee.setActive(request.active());
    }

    private static List<DayInterval> sorted(List<DayInterval> intervals) {
        return intervals.stream()
                .sorted(Comparator.comparing(DayInterval::dayOfWeek).thenComparing(DayInterval::startTime))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
