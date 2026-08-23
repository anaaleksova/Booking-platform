package com.bookingsaas.booking;

import com.bookingsaas.booking.dto.BookingResponse;
import com.bookingsaas.booking.dto.CancelBookingRequest;
import com.bookingsaas.booking.dto.CreateBookingRequest;
import com.bookingsaas.booking.dto.CustomerHistoryResponse;
import com.bookingsaas.booking.dto.RescheduleBookingRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Booking management for the dashboard.
 *
 * <p>Cancellation is a status change, not a delete: the history is what a
 * business runs on. {@code DELETE} is mapped to it because that is the verb the
 * REST conventions expect, but no row is removed.
 */
@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/bookings")
    public List<BookingResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) BookingStatus status) {
        return bookingService.list(from, to, staffId, customerId, status);
    }

    @GetMapping("/bookings/{id}")
    public BookingResponse get(@PathVariable Long id) {
        return bookingService.get(id);
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request) {
        return bookingService.createFromDashboard(request);
    }

    @PutMapping("/bookings/{id}/reschedule")
    public BookingResponse reschedule(@PathVariable Long id,
                                      @Valid @RequestBody RescheduleBookingRequest request) {
        return bookingService.reschedule(id, request);
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, @RequestBody(required = false) CancelBookingRequest request) {
        return bookingService.cancel(id, request == null ? null : request.reason());
    }

    @DeleteMapping("/bookings/{id}")
    public BookingResponse cancelViaDelete(@PathVariable Long id) {
        return bookingService.cancel(id, null);
    }

    @PostMapping("/bookings/{id}/complete")
    public BookingResponse complete(@PathVariable Long id) {
        return bookingService.markCompleted(id);
    }

    @PostMapping("/bookings/{id}/no-show")
    public BookingResponse noShow(@PathVariable Long id) {
        return bookingService.markNoShow(id);
    }

    /** Everything this business has ever booked for one customer. */
    @GetMapping("/customers/{id}/history")
    public CustomerHistoryResponse customerHistory(@PathVariable Long id) {
        return bookingService.customerHistory(id);
    }
}
