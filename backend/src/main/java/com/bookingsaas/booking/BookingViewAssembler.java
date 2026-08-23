package com.bookingsaas.booking;

import com.bookingsaas.booking.dto.BookingResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerRepository;
import com.bookingsaas.resource.Resource;
import com.bookingsaas.resource.ResourceRepository;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.BookableServiceRepository;
import com.bookingsaas.staff.Staff;
import com.bookingsaas.staff.StaffRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns bookings into API responses, resolving the names a calendar needs.
 *
 * <p>Lookups are batched per page of results rather than done per booking, so
 * a month view costs a handful of queries instead of hundreds. Bookings hold
 * plain ids rather than JPA associations precisely so this stays explicit.
 */
@Component
public class BookingViewAssembler {

    private final BookableServiceRepository serviceRepository;
    private final StaffRepository staffRepository;
    private final ResourceRepository resourceRepository;
    private final CustomerRepository customerRepository;

    public BookingViewAssembler(BookableServiceRepository serviceRepository,
                                StaffRepository staffRepository,
                                ResourceRepository resourceRepository,
                                CustomerRepository customerRepository) {
        this.serviceRepository = serviceRepository;
        this.staffRepository = staffRepository;
        this.resourceRepository = resourceRepository;
        this.customerRepository = customerRepository;
    }

    public BookingResponse toResponse(Business business, Booking booking) {
        return toResponses(business, List.of(booking)).getFirst();
    }

    public List<BookingResponse> toResponses(Business business, List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        Long businessId = business.getId();
        ZoneId zone = business.zoneId();

        // Each lookup is skipped entirely when no booking references that kind
        // of row — a resource-free salon never queries the resource table.
        Map<Long, BookableService> services = lookup(ids(bookings, Booking::getServiceId),
                batch -> serviceRepository.findByBusinessIdAndIdIn(businessId, batch), BookableService::getId);
        Map<Long, Staff> staff = lookup(ids(bookings, Booking::getStaffId),
                batch -> staffRepository.findByBusinessIdAndIdInOrderByNameAsc(businessId, batch), Staff::getId);
        Map<Long, Resource> resources = lookup(ids(bookings, Booking::getResourceId),
                batch -> resourceRepository.findByBusinessIdAndIdInOrderByNameAsc(businessId, batch),
                Resource::getId);
        Map<Long, Customer> customers = lookup(ids(bookings, Booking::getCustomerId),
                batch -> customerRepository.findByBusinessIdAndIdIn(businessId, batch), Customer::getId);

        return bookings.stream().map(booking -> {
            BookableService service = services.get(booking.getServiceId());
            Staff member = booking.getStaffId() == null ? null : staff.get(booking.getStaffId());
            Resource resource = booking.getResourceId() == null ? null : resources.get(booking.getResourceId());
            Customer customer = customers.get(booking.getCustomerId());

            return new BookingResponse(
                    booking.getId(),
                    booking.getServiceId(),
                    service == null ? null : service.getName(),
                    booking.getStaffId(),
                    member == null ? null : member.getName(),
                    booking.getResourceId(),
                    resource == null ? null : resource.getName(),
                    booking.getCustomerId(),
                    customer == null ? null : customer.getName(),
                    customer == null ? null : customer.getPhone(),
                    customer == null ? null : customer.getEmail(),
                    booking.getStartDateTime(),
                    booking.getEndDateTime(),
                    LocalDateTime.ofInstant(booking.getStartDateTime(), zone),
                    LocalDateTime.ofInstant(booking.getEndDateTime(), zone),
                    business.getTimezone(),
                    booking.getStatus().name(),
                    booking.getSource().name(),
                    booking.getCustomerNotes(),
                    booking.getInternalNotes(),
                    booking.getPrice(),
                    booking.getCurrency(),
                    booking.getPublicReference(),
                    booking.getCreatedAt());
        }).toList();
    }

    private static List<Long> ids(List<Booking> bookings, Function<Booking, Long> extractor) {
        return bookings.stream().map(extractor).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<Long, T> lookup(List<Long> ids,
                                           Function<List<Long>, Collection<T>> loader,
                                           Function<T, Long> key) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return loader.apply(ids).stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
