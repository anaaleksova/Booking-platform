package com.bookingsaas.integration.api;

import com.bookingsaas.business.Business;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerService;
import com.bookingsaas.integration.api.IntegrationDtos.AssignedResource;
import com.bookingsaas.integration.api.IntegrationDtos.CustomerChannel;
import com.bookingsaas.integration.api.IntegrationDtos.ReservationInfo;
import com.bookingsaas.integration.conversation.ConversationRepository;
import com.bookingsaas.integration.identity.CustomerExternalIdentityRepository;
import com.bookingsaas.reservation.Reservation;
import com.bookingsaas.reservation.ReservationService;
import com.bookingsaas.reservation.ReservationViewAssembler;
import com.bookingsaas.reservation.dto.ReservationResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reservations as the integration API shows them. */
@Component
public class IntegrationReservationViews {

    private final ReservationViewAssembler assembler;
    private final ReservationService reservationService;
    private final CustomerService customerService;
    private final CustomerExternalIdentityRepository identities;
    private final ConversationRepository conversations;
    private final IntegrationCatalogService catalog;

    public IntegrationReservationViews(ReservationViewAssembler assembler,
                                       ReservationService reservationService,
                                       CustomerService customerService,
                                       CustomerExternalIdentityRepository identities,
                                       ConversationRepository conversations,
                                       IntegrationCatalogService catalog) {
        this.assembler = assembler;
        this.reservationService = reservationService;
        this.customerService = customerService;
        this.identities = identities;
        this.conversations = conversations;
        this.catalog = catalog;
    }

    public ReservationInfo toInfo(Business business, Reservation reservation) {
        ReservationResponse view = assembler.toResponse(business, reservation);
        Customer customer = customerService.requireForBusiness(business.getId(), reservation.getCustomerId());
        return new ReservationInfo(
                view.id(),
                view.reference(),
                business.getId(),
                view.status(),
                view.source(),
                view.serviceId(),
                view.serviceName(),
                view.customerId(),
                view.customerName(),
                customer.getFirstName(),
                view.numberOfParticipants(),
                view.localStartTime().toLocalDate(),
                view.localStartTime().toLocalTime().format(IntegrationCatalogService.HH_MM),
                view.localEndTime().toLocalTime().format(IntegrationCatalogService.HH_MM),
                view.localStartTime(),
                view.localEndTime(),
                view.timezone(),
                view.resources().stream()
                        .map(resource -> new AssignedResource(resource.resourceTypeName(), resource.name()))
                        .toList(),
                view.originalPrice(),
                view.discountAmount(),
                view.finalPrice(),
                view.currency(),
                view.discountCode(),
                reservationService.isCancellableByCustomer(business, reservation),
                reservation.getCancellationReason(),
                catalog.manageUrl(view.reference().toString()),
                channelsOf(business.getId(), reservation));
    }

    /**
     * Where the customer can be messaged: their linked social identities, plus
     * the chat the reservation was made in (which may not be linked when the
     * details matched an existing customer).
     */
    private List<CustomerChannel> channelsOf(Long businessId, Reservation reservation) {
        Set<CustomerChannel> channels = new LinkedHashSet<>();
        identities.findByBusinessIdAndCustomerId(businessId, reservation.getCustomerId())
                .forEach(identity -> channels.add(new CustomerChannel(identity.getProvider(),
                        identity.getExternalUserId())));
        conversations.findByBusinessIdAndReservationId(businessId, reservation.getId())
                .forEach(conversation -> channels.add(new CustomerChannel(conversation.getProvider(),
                        conversation.getExternalUserId())));
        return List.copyOf(channels);
    }
}
