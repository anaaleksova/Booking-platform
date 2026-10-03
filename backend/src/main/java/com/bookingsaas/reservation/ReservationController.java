package com.bookingsaas.reservation;

import com.bookingsaas.availability.dto.SlotResponse;
import com.bookingsaas.reservation.dto.AdminReservationRequest;
import com.bookingsaas.reservation.dto.CustomerHistoryResponse;
import com.bookingsaas.reservation.dto.RescheduleRequest;
import com.bookingsaas.reservation.dto.ReservationResponse;
import com.bookingsaas.reservation.dto.StatusChangeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Reservation management for the admin panel.
 *
 * <p>Cancellation is a status change, not a delete: the history is what a
 * business runs on, and the assigned resources are simply released.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin · Reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationQueryService queryService;

    public ReservationController(ReservationService reservationService, ReservationQueryService queryService) {
        this.reservationService = reservationService;
        this.queryService = queryService;
    }

    @GetMapping("/reservations")
    @Operation(summary = "List reservations starting in a date range (defaults to 30 days back, 60 ahead)")
    public List<ReservationResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long resourceId) {
        return queryService.list(new ReservationQueryService.Filter(from, to, status, serviceId, customerId,
                resourceId));
    }

    @GetMapping("/reservations/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return queryService.get(id);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody AdminReservationRequest request) {
        return reservationService.createFromAdmin(request);
    }

    @PatchMapping("/reservations/{id}")
    @Operation(summary = "Confirm, complete or cancel a reservation")
    public ReservationResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return reservationService.changeStatus(id, request.status(), request.reason());
    }

    @PutMapping("/reservations/{id}/schedule")
    @Operation(summary = "Move a reservation and/or change its assigned resources")
    public ReservationResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request) {
        return reservationService.reschedule(id, request);
    }

    @GetMapping("/reservations/{id}/resource-options")
    @Operation(summary = "Resources free for this reservation at its current time")
    public SlotResponse resourceOptions(@PathVariable Long id) {
        return queryService.resourceOptions(id);
    }

    /** Everything this business has booked for one customer. */
    @GetMapping("/customers/{id}/reservations")
    public CustomerHistoryResponse customerHistory(@PathVariable Long id) {
        return queryService.customerHistory(id);
    }
}
