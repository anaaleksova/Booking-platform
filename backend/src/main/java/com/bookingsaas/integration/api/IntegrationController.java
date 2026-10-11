package com.bookingsaas.integration.api;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.integration.api.IntegrationDtos.AvailabilityResponse;
import com.bookingsaas.integration.api.IntegrationDtos.BusinessInfo;
import com.bookingsaas.integration.api.IntegrationDtos.CancelReservationRequest;
import com.bookingsaas.integration.api.IntegrationDtos.CreateReservationRequest;
import com.bookingsaas.integration.api.IntegrationDtos.CustomerInfo;
import com.bookingsaas.integration.api.IntegrationDtos.CustomerRequest;
import com.bookingsaas.integration.api.IntegrationDtos.Quote;
import com.bookingsaas.integration.api.IntegrationDtos.QuoteRequest;
import com.bookingsaas.integration.api.IntegrationDtos.ReservationInfo;
import com.bookingsaas.integration.api.IntegrationDtos.ServiceInfo;
import com.bookingsaas.integration.api.IntegrationReservationService.CreateResult;
import com.bookingsaas.integration.auth.IntegrationAccess;
import com.bookingsaas.integration.auth.IntegrationPermission;
import com.bookingsaas.integration.auth.IntegrationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The integration API for automation tools such as n8n.
 *
 * <p>Authenticated with an integration token (never a user session). Every
 * endpoint names its business and checks it, and the permission it needs,
 * against the token before doing anything. The answers come from the same
 * services as the website: no availability, pricing or booking rule exists
 * here.
 */
@RestController
@RequestMapping("/api/integrations")
@Tag(name = "Integrations · Automation API")
public class IntegrationController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    private final IntegrationAccess access;
    private final IntegrationCatalogService catalog;
    private final IntegrationReservationService reservations;

    public IntegrationController(IntegrationAccess access,
                                 IntegrationCatalogService catalog,
                                 IntegrationReservationService reservations) {
        this.access = access;
        this.catalog = catalog;
        this.reservations = reservations;
    }

    /** Which business this token acts for — the first call a workflow makes. */
    public record TokenInfo(Long businessId, String tokenName, Set<IntegrationPermission> permissions) {
    }

    @GetMapping("/me")
    @Operation(summary = "The business and permissions behind the presented token")
    public TokenInfo me() {
        IntegrationPrincipal principal = access.principal();
        return new TokenInfo(principal.businessId(), principal.tokenName(), principal.permissions());
    }

    @GetMapping("/businesses/{businessId}")
    public BusinessInfo business(@PathVariable Long businessId) {
        access.require(businessId, IntegrationPermission.READ_BUSINESS);
        return catalog.business(businessId);
    }

    @GetMapping("/businesses/{businessId}/services")
    public List<ServiceInfo> services(@PathVariable Long businessId) {
        access.require(businessId, IntegrationPermission.READ_SERVICES);
        return catalog.services(businessId);
    }

    @GetMapping("/businesses/{businessId}/services/{serviceId}")
    public ServiceInfo service(@PathVariable Long businessId, @PathVariable Long serviceId) {
        access.require(businessId, IntegrationPermission.READ_SERVICES);
        return catalog.service(businessId, serviceId);
    }

    @GetMapping("/businesses/{businessId}/availability")
    @Operation(summary = "Bookable start times on one day, from the same engine as the website")
    public AvailabilityResponse availability(
            @PathVariable Long businessId,
            @RequestParam Long serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int numberOfParticipants) {
        access.require(businessId, IntegrationPermission.READ_AVAILABILITY);
        ServiceInfo service = catalog.service(businessId, serviceId);
        return new AvailabilityResponse(serviceId, date, numberOfParticipants, service.durationMinutes(),
                catalog.business(businessId).timezone(),
                catalog.slots(businessId, serviceId, date, numberOfParticipants));
    }

    @PostMapping("/businesses/{businessId}/quote")
    @Operation(summary = "The server's price for a service and group, validating an optional discount code")
    public Quote quote(@PathVariable Long businessId, @Valid @RequestBody QuoteRequest request) {
        access.require(businessId, IntegrationPermission.READ_AVAILABILITY);
        return catalog.quote(businessId, request.serviceId(), request.numberOfParticipants(), request.discountCode());
    }

    // --- customers ---------------------------------------------------------

    @PostMapping("/businesses/{businessId}/customers")
    @Operation(summary = "Find or create the customer behind a social user")
    public CustomerInfo findOrCreateCustomer(@PathVariable Long businessId,
                                             @Valid @RequestBody CustomerRequest request) {
        access.require(businessId, IntegrationPermission.CREATE_CUSTOMER);
        return reservations.findOrCreateCustomer(businessId, request);
    }

    @GetMapping("/businesses/{businessId}/customers")
    @Operation(summary = "Look a customer up by social identity, phone or email")
    public CustomerInfo findCustomer(@PathVariable Long businessId,
                                     @RequestParam(required = false) ChannelProvider provider,
                                     @RequestParam(required = false) String externalUserId,
                                     @RequestParam(required = false) String phone,
                                     @RequestParam(required = false) String email) {
        access.require(businessId, IntegrationPermission.READ_CUSTOMER);
        return reservations.findCustomer(businessId, provider, externalUserId, phone, email)
                .orElseThrow(() -> ApiException.notFound("No customer matches"));
    }

    @GetMapping("/businesses/{businessId}/customers/{customerId}/reservations")
    @Operation(summary = "A customer's upcoming reservations")
    public List<ReservationInfo> upcoming(@PathVariable Long businessId, @PathVariable Long customerId) {
        access.require(businessId, IntegrationPermission.READ_RESERVATION);
        return reservations.upcomingFor(businessId, customerId);
    }

    // --- reservations --------------------------------------------------------

    @PostMapping("/businesses/{businessId}/reservations")
    @Operation(summary = "Create a reservation with the website's rules; idempotent per Idempotency-Key")
    public ResponseEntity<ReservationInfo> create(@PathVariable Long businessId,
                                                  @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
                                                  @Valid @RequestBody CreateReservationRequest request) {
        access.require(businessId, IntegrationPermission.CREATE_RESERVATION);
        if (request.externalCustomer() != null) {
            access.require(businessId, IntegrationPermission.CREATE_CUSTOMER);
        }
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.validation("Idempotency-Key must be 8-128 characters: letters, digits, . _ : -");
        }
        CreateResult result;
        try {
            result = reservations.create(businessId, idempotencyKey, request);
        } catch (DataIntegrityViolationException ex) {
            if (!IntegrationReservationService.isIdempotencyRace(ex)) {
                throw ex;
            }
            // A simultaneous request with this key won; answer with its result.
            result = reservations.replay(businessId, idempotencyKey, request);
        }
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.reservation());
    }

    @GetMapping("/businesses/{businessId}/reservations/{reservationId}")
    public ReservationInfo reservation(@PathVariable Long businessId, @PathVariable Long reservationId) {
        access.require(businessId, IntegrationPermission.READ_RESERVATION);
        return reservations.get(businessId, reservationId);
    }

    @PostMapping("/businesses/{businessId}/reservations/{reservationId}/cancel")
    @Operation(summary = "Cancel on the customer's behalf, under the business's cancellation policy")
    public ReservationInfo cancel(@PathVariable Long businessId, @PathVariable Long reservationId,
                                  @Valid @RequestBody(required = false) CancelReservationRequest request) {
        access.require(businessId, IntegrationPermission.CANCEL_RESERVATION);
        return reservations.cancel(businessId, reservationId, request);
    }
}
