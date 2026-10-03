package com.bookingsaas.availability;

import com.bookingsaas.availability.dto.DaySlotsResponse;
import com.bookingsaas.common.security.CurrentTenant;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Availability for the admin panel — used when taking or moving a reservation.
 *
 * <p>Lead-time rules are not applied here: a business taking a reservation over
 * the phone may fill a slot that starts sooner than customers may book.
 */
@RestController
@RequestMapping("/api/admin/availability")
@Tag(name = "Admin · Reservations")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final CurrentTenant currentTenant;

    public AvailabilityController(AvailabilityService availabilityService, CurrentTenant currentTenant) {
        this.availabilityService = availabilityService;
        this.currentTenant = currentTenant;
    }

    @GetMapping
    public List<DaySlotsResponse> search(
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int participants,
            @RequestParam(required = false) Long excludeReservationId) {
        return availabilityService.search(currentTenant.businessId(),
                new AvailabilityQuery(serviceId, from, to, participants, excludeReservationId), false);
    }
}
