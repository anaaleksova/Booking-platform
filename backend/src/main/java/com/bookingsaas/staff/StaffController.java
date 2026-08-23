package com.bookingsaas.staff;

import com.bookingsaas.common.domain.DayInterval;
import com.bookingsaas.staff.dto.StaffRequest;
import com.bookingsaas.staff.dto.StaffResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public List<StaffResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return staffService.list(activeOnly);
    }

    @GetMapping("/{id}")
    public StaffResponse get(@PathVariable Long id) {
        return staffService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@Valid @RequestBody StaffRequest request) {
        return staffService.create(request);
    }

    @PutMapping("/{id}")
    public StaffResponse update(@PathVariable Long id, @Valid @RequestBody StaffRequest request) {
        return staffService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        staffService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/services")
    public StaffResponse replaceServices(@PathVariable Long id, @RequestBody List<Long> serviceIds) {
        return staffService.replaceServices(id, serviceIds);
    }

    @GetMapping("/{id}/working-hours")
    public List<DayInterval> workingHours(@PathVariable Long id) {
        return staffService.workingHours(id);
    }

    /** Replaces the whole week in one call — partial edits cannot leave gaps. */
    @PutMapping("/{id}/working-hours")
    public List<DayInterval> replaceWorkingHours(@PathVariable Long id,
                                                 @Valid @RequestBody List<DayInterval> intervals) {
        return staffService.replaceWorkingHours(id, intervals);
    }
}
