package com.bookingsaas.reservation;

import com.bookingsaas.business.Business;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerRepository;
import com.bookingsaas.reservation.dto.PublicReservationResponse;
import com.bookingsaas.reservation.dto.ReservationResponse;
import com.bookingsaas.reservation.dto.ReservationResponse.AssignedResource;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceService;
import com.bookingsaas.resource.ResourceType;
import com.bookingsaas.resource.ResourceTypeService;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns reservations into API responses, resolving the names a calendar needs.
 *
 * <p>Lookups are batched per page of results rather than done per reservation,
 * so a month view costs a handful of queries instead of hundreds.
 */
@Component
public class ReservationViewAssembler {

    private final ServiceCatalogService serviceCatalog;
    private final CustomerRepository customerRepository;
    private final ResourceService resourceService;
    private final ResourceTypeService resourceTypeService;

    public ReservationViewAssembler(ServiceCatalogService serviceCatalog,
                                    CustomerRepository customerRepository,
                                    ResourceService resourceService,
                                    ResourceTypeService resourceTypeService) {
        this.serviceCatalog = serviceCatalog;
        this.customerRepository = customerRepository;
        this.resourceService = resourceService;
        this.resourceTypeService = resourceTypeService;
    }

    public ReservationResponse toResponse(Business business, Reservation reservation) {
        return toResponses(business, List.of(reservation)).getFirst();
    }

    public List<ReservationResponse> toResponses(Business business, List<Reservation> reservations) {
        if (reservations.isEmpty()) {
            return List.of();
        }
        Long businessId = business.getId();
        ZoneId zone = business.zoneId();

        Map<Long, BookableService> services = index(serviceCatalog.byIds(businessId,
                distinct(reservations.stream().map(Reservation::getServiceId).toList())), BookableService::getId);
        Map<Long, Customer> customers = index(customerRepository.findByBusinessIdAndIdIn(businessId,
                distinct(reservations.stream().map(Reservation::getCustomerId).toList())), Customer::getId);
        Names names = names(businessId, reservations.stream().flatMap(r -> r.getResources().stream()).toList());

        return reservations.stream().map(reservation -> {
            BookableService service = services.get(reservation.getServiceId());
            Customer customer = customers.get(reservation.getCustomerId());
            return new ReservationResponse(
                    reservation.getId(),
                    reservation.getPublicReference(),
                    reservation.getServiceId(),
                    service == null ? null : service.getName(),
                    reservation.getCustomerId(),
                    customer == null ? null : customer.fullName(),
                    customer == null ? null : customer.getEmail(),
                    customer == null ? null : customer.getPhone(),
                    reservation.getNumberOfParticipants(),
                    reservation.getStartTime(),
                    reservation.getEndTime(),
                    LocalDateTime.ofInstant(reservation.getStartTime(), zone),
                    LocalDateTime.ofInstant(reservation.getEndTime(), zone),
                    business.getTimezone(),
                    reservation.getStatus().name(),
                    reservation.getSource().name(),
                    reservation.getOriginalPrice(),
                    reservation.getDiscountAmount(),
                    reservation.getFinalPrice(),
                    reservation.getCurrency(),
                    reservation.getDiscountCode(),
                    names.assigned(reservation.getResources()),
                    reservation.getCustomerNotes(),
                    reservation.getInternalNotes(),
                    reservation.getCreatedAt(),
                    reservation.getCancelledAt(),
                    reservation.getCancellationReason());
        }).toList();
    }

    public PublicReservationResponse toPublic(Business business, Reservation reservation, boolean cancellable) {
        ZoneId zone = business.zoneId();
        BookableService service = serviceCatalog.requireForBusiness(business.getId(), reservation.getServiceId());
        Customer customer = customerRepository.findByIdAndBusinessId(reservation.getCustomerId(), business.getId())
                .orElse(null);
        List<PublicReservationResponse.Resource> resources = names(business.getId(), reservation.getResources())
                .assigned(reservation.getResources()).stream()
                .map(item -> new PublicReservationResponse.Resource(item.resourceTypeName(), item.name()))
                .toList();

        return new PublicReservationResponse(
                reservation.getPublicReference(),
                business.getName(),
                business.getSlug(),
                business.getAddress(),
                business.getPhone(),
                service.getName(),
                reservation.getNumberOfParticipants(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                LocalDateTime.ofInstant(reservation.getStartTime(), zone),
                LocalDateTime.ofInstant(reservation.getEndTime(), zone),
                business.getTimezone(),
                reservation.getStatus().name(),
                reservation.getOriginalPrice(),
                reservation.getDiscountAmount(),
                reservation.getFinalPrice(),
                reservation.getCurrency(),
                reservation.getDiscountCode(),
                customer == null ? null : customer.fullName(),
                resources,
                cancellable);
    }

    /** "Room 2, Anna, Maria" — for notifications. */
    public String resourceSummary(Long businessId, Reservation reservation) {
        return names(businessId, reservation.getResources()).assigned(reservation.getResources()).stream()
                .map(AssignedResource::name)
                .collect(Collectors.joining(", "));
    }

    // --- internals ---------------------------------------------------------

    private record Names(Map<Long, Resource> resources, Map<Long, ResourceType> types) {

        List<AssignedResource> assigned(Collection<ReservationResource> held) {
            return held.stream()
                    .map(item -> {
                        Resource resource = resources.get(item.getResourceId());
                        ResourceType type = resource == null ? null : types.get(resource.getResourceTypeId());
                        return new AssignedResource(item.getResourceId(),
                                resource == null ? "Removed resource" : resource.getName(),
                                resource == null ? null : resource.getResourceTypeId(),
                                type == null ? null : type.getName(),
                                item.getRequirementId());
                    })
                    .toList();
        }
    }

    private Names names(Long businessId, Collection<ReservationResource> held) {
        List<Long> ids = distinct(held.stream().map(ReservationResource::getResourceId).toList());
        Map<Long, Resource> resources = index(resourceService.byIds(businessId, ids), Resource::getId);
        Map<Long, ResourceType> types = resourceTypeService.byIds(businessId,
                distinct(resources.values().stream().map(Resource::getResourceTypeId).toList()));
        return new Names(resources, types);
    }

    private static List<Long> distinct(List<Long> ids) {
        return ids.stream().distinct().toList();
    }

    private static <T> Map<Long, T> index(Collection<T> rows, Function<T, Long> key) {
        return rows.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
