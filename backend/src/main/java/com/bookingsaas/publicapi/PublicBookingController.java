package com.bookingsaas.publicapi;

import com.bookingsaas.availability.AvailabilityQuery;
import com.bookingsaas.availability.AvailabilityService;
import com.bookingsaas.availability.dto.DayAvailability;
import com.bookingsaas.booking.BookingService;
import com.bookingsaas.booking.dto.CancelBookingRequest;
import com.bookingsaas.booking.dto.PublicBookingRequest;
import com.bookingsaas.booking.dto.PublicBookingResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.publicapi.dto.PublicBusinessResponse;
import com.bookingsaas.publicapi.dto.PublicServiceResponse;
import com.bookingsaas.publicapi.dto.PublicStaffResponse;
import com.bookingsaas.service.ServiceCatalogService;
import com.bookingsaas.staff.StaffService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The customer-facing API behind {@code /book/{slug}}.
 *
 * <p>Unauthenticated by design, and therefore deliberately thin: it composes
 * the same module services the dashboard uses, exposes only public projections,
 * and resolves the tenant from the slug — the one place a client-supplied
 * identifier chooses a business, which is safe because everything it can reach
 * is public anyway.
 *
 * <p>Customer bookings go through the strict rule set: minimum notice, maximum
 * advance and active-service checks all apply.
 */
@RestController
@RequestMapping("/api/public")
public class PublicBookingController {

    private final BusinessService businessService;
    private final ServiceCatalogService serviceCatalog;
    private final StaffService staffService;
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;

    public PublicBookingController(BusinessService businessService,
                                   ServiceCatalogService serviceCatalog,
                                   StaffService staffService,
                                   AvailabilityService availabilityService,
                                   BookingService bookingService) {
        this.businessService = businessService;
        this.serviceCatalog = serviceCatalog;
        this.staffService = staffService;
        this.availabilityService = availabilityService;
        this.bookingService = bookingService;
    }

    @GetMapping("/{slug}")
    public PublicBusinessResponse business(@PathVariable String slug) {
        return PublicBusinessResponse.from(businessService.requireActiveBySlug(slug));
    }

    @GetMapping("/{slug}/services")
    public List<PublicServiceResponse> services(@PathVariable String slug) {
        Business business = businessService.requireActiveBySlug(slug);
        return serviceCatalog.activeServicesOf(business.getId()).stream()
                .map(PublicServiceResponse::from)
                .toList();
    }

    /** Who can deliver a service — the "or any available" list on step 3. */
    @GetMapping("/{slug}/services/{serviceId}/staff")
    public List<PublicStaffResponse> staffForService(@PathVariable String slug, @PathVariable Long serviceId) {
        Business business = businessService.requireActiveBySlug(slug);
        serviceCatalog.requireBookable(business.getId(), serviceId);
        return staffService.eligibleStaffFor(business.getId(), serviceId).stream()
                .map(PublicStaffResponse::from)
                .toList();
    }

    @GetMapping("/{slug}/availability")
    public List<DayAvailability> availability(
            @PathVariable String slug,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long staffId) {
        Business business = businessService.requireActiveBySlug(slug);
        return availabilityService.search(business.getId(),
                AvailabilityQuery.of(serviceId, from, to, staffId, null), true);
    }

    @PostMapping("/{slug}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicBookingResponse book(@PathVariable String slug, @Valid @RequestBody PublicBookingRequest request) {
        return bookingService.createFromPublicPage(slug, request);
    }

    /** The confirmation page. The reference is the only credential needed. */
    @GetMapping("/bookings/{reference}")
    public PublicBookingResponse booking(@PathVariable UUID reference) {
        return bookingService.getByReference(reference);
    }

    @PostMapping("/bookings/{reference}/cancel")
    public PublicBookingResponse cancel(@PathVariable UUID reference,
                                        @RequestBody(required = false) CancelBookingRequest request) {
        return bookingService.cancelByReference(reference, request == null ? null : request.reason());
    }
}
