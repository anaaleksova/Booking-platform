package com.bookingsaas.publicapi;

import com.bookingsaas.availability.AvailabilityQuery;
import com.bookingsaas.availability.AvailabilityService;
import com.bookingsaas.availability.dto.DaySlotsResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.GalleryService;
import com.bookingsaas.business.OpeningHoursService;
import com.bookingsaas.pricing.PricingService;
import com.bookingsaas.publicapi.dto.PublicBusinessResponse;
import com.bookingsaas.publicapi.dto.PublicServiceResponse;
import com.bookingsaas.pricing.dto.QuoteRequest;
import com.bookingsaas.pricing.dto.QuoteResponse;
import com.bookingsaas.reservation.ReservationQueryService;
import com.bookingsaas.reservation.ReservationService;
import com.bookingsaas.reservation.dto.CancelRequest;
import com.bookingsaas.reservation.dto.PublicReservationRequest;
import com.bookingsaas.reservation.dto.PublicReservationResponse;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * The customer-facing API.
 *
 * <p>Unauthenticated by design, and therefore deliberately thin: it composes
 * the same module services the admin panel uses, exposes only public
 * projections, and resolves the tenant from the slug — the one place a
 * client-supplied identifier chooses a business, which is safe because
 * everything reachable here is public anyway.
 *
 * <p>Customer reservations go through the strict rule set: minimum notice,
 * maximum advance and active-service checks all apply, and prices are always
 * computed here.
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public · Customer booking")
public class PublicReservationController {

    private final BusinessService businessService;
    private final OpeningHoursService openingHoursService;
    private final ServiceCatalogService serviceCatalog;
    private final AvailabilityService availabilityService;
    private final PricingService pricingService;
    private final ReservationService reservationService;
    private final ReservationQueryService reservationQueryService;
    private final BookingPageService bookingPageService;
    private final GalleryService galleryService;

    public PublicReservationController(BusinessService businessService,
                                       OpeningHoursService openingHoursService,
                                       ServiceCatalogService serviceCatalog,
                                       AvailabilityService availabilityService,
                                       PricingService pricingService,
                                       ReservationService reservationService,
                                       ReservationQueryService reservationQueryService,
                                       BookingPageService bookingPageService,
                                       GalleryService galleryService) {
        this.businessService = businessService;
        this.openingHoursService = openingHoursService;
        this.serviceCatalog = serviceCatalog;
        this.availabilityService = availabilityService;
        this.pricingService = pricingService;
        this.reservationService = reservationService;
        this.reservationQueryService = reservationQueryService;
        this.bookingPageService = bookingPageService;
        this.galleryService = galleryService;
    }

    @GetMapping("/businesses/{slug}")
    public PublicBusinessResponse business(@PathVariable String slug) {
        Business business = businessService.requireActiveBySlug(slug);
        return PublicBusinessResponse.from(business, openingHoursService.intervalsOf(business.getId()),
                galleryService.publicGallery(business));
    }

    @GetMapping("/businesses/{slug}/services")
    public List<PublicServiceResponse> services(@PathVariable String slug) {
        return bookingPageService.services(businessService.requireActiveBySlug(slug));
    }

    @GetMapping("/businesses/{slug}/services/{serviceId}")
    public PublicServiceResponse service(@PathVariable String slug, @PathVariable Long serviceId) {
        return bookingPageService.service(businessService.requireActiveBySlug(slug), serviceId);
    }

    @GetMapping("/businesses/{slug}/services/{serviceId}/availability")
    @Operation(summary = "Reservable start times for a group size, per day")
    public List<DaySlotsResponse> availability(
            @PathVariable String slug,
            @PathVariable Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int participants) {
        Business business = businessService.requireActiveBySlug(slug);
        return availabilityService.search(business.getId(),
                new AvailabilityQuery(serviceId, from, to, participants, null), true);
    }

    @PostMapping("/businesses/{slug}/quote")
    @Operation(summary = "Price summary, validating an optional discount code")
    public QuoteResponse quote(@PathVariable String slug, @Valid @RequestBody QuoteRequest request) {
        Business business = businessService.requireActiveBySlug(slug);
        BookableService service = serviceCatalog.requireBookable(business.getId(), request.serviceId());
        return QuoteResponse.from(pricingService.quote(business, service, request.participants(),
                request.discountCode()));
    }

    @PostMapping("/businesses/{slug}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicReservationResponse reserve(@PathVariable String slug,
                                             @Valid @RequestBody PublicReservationRequest request) {
        return reservationService.createFromCustomer(slug, request);
    }

    /** The reservation details page. The reference is the only credential needed. */
    @GetMapping("/reservations/{reference}")
    public PublicReservationResponse reservation(@PathVariable UUID reference) {
        return reservationQueryService.getByReference(reference);
    }

    @PostMapping("/reservations/{reference}/cancel")
    public PublicReservationResponse cancel(@PathVariable UUID reference,
                                            @Valid @RequestBody(required = false) CancelRequest request) {
        return reservationService.cancelByCustomer(reference, request == null ? null : request.reason());
    }
}
