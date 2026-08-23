package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.DayAvailability;
import com.bookingsaas.common.security.CurrentTenant;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Availability for the dashboard — used by manual booking and rescheduling.
 *
 * <p>Lead-time rules are not applied here: a business taking a booking over the
 * phone is allowed to fill a slot that starts sooner than customers may book.
 */
@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final CurrentTenant currentTenant;

    public AvailabilityController(AvailabilityService availabilityService, CurrentTenant currentTenant) {
        this.availabilityService = availabilityService;
        this.currentTenant = currentTenant;
    }

    @GetMapping
    public List<DayAvailability> search(
            @RequestParam @NotNull Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) Long excludeBookingId) {
        AvailabilityQuery query = new AvailabilityQuery(serviceId, from, to, staffId, resourceId, excludeBookingId);
        return availabilityService.search(currentTenant.businessId(), query, false);
    }
}
