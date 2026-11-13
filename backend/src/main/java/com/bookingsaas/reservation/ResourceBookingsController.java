package com.bookingsaas.reservation;

import com.bookingsaas.availability.spi.ScheduleConflict;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The bookings a resource (or employee, through its resource) still holds. */
@RestController
@RequestMapping("/api/admin/resources")
@Tag(name = "Admin · Reservations")
public class ResourceBookingsController {

    private final ResourceBookingsService service;

    public ResourceBookingsController(ResourceBookingsService service) {
        this.service = service;
    }

    @GetMapping("/{id}/upcoming-reservations")
    @Operation(summary = "Bookings not yet over that hold this resource")
    public List<ScheduleConflict> upcoming(@PathVariable Long id) {
        return service.upcoming(id);
    }

    @GetMapping("/stranded-reservations")
    @Operation(summary = "Inactive resources that still have upcoming bookings, with how many")
    public List<StrandedBookings> stranded() {
        return service.stranded();
    }

    @PostMapping("/{id}/reassign-reservations")
    @Operation(summary = "Give a deactivated resource's upcoming bookings to free resources at the same times")
    public ResourceBookingsService.ReassignmentResult reassign(@PathVariable Long id) {
        return service.reassignAway(id);
    }
}
