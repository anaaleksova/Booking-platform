package com.bookingsaas.integration.api;

import com.bookingsaas.availability.AvailabilityQuery;
import com.bookingsaas.availability.AvailabilityService;
import com.bookingsaas.availability.dto.DaySlotsResponse;
import com.bookingsaas.availability.dto.SlotResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.business.BusinessSettings;
import com.bookingsaas.business.OpeningHoursService;
import com.bookingsaas.common.config.AppProperties;
import com.bookingsaas.integration.IntegrationProvider;
import com.bookingsaas.integration.IntegrationRepository;
import com.bookingsaas.integration.api.IntegrationDtos.BusinessInfo;
import com.bookingsaas.integration.api.IntegrationDtos.Channel;
import com.bookingsaas.integration.api.IntegrationDtos.ChoosableRequirement;
import com.bookingsaas.integration.api.IntegrationDtos.NamedResource;
import com.bookingsaas.integration.api.IntegrationDtos.Policies;
import com.bookingsaas.integration.api.IntegrationDtos.Quote;
import com.bookingsaas.integration.api.IntegrationDtos.ResourceChoice;
import com.bookingsaas.integration.api.IntegrationDtos.ResourceOptions;
import com.bookingsaas.integration.api.IntegrationDtos.ServiceInfo;
import com.bookingsaas.integration.api.IntegrationDtos.Slot;
import com.bookingsaas.pricing.PriceQuote;
import com.bookingsaas.pricing.PricingService;
import com.bookingsaas.publicapi.BookingPageService;
import com.bookingsaas.publicapi.dto.PublicServiceResponse;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read side of the integration API. A translation layer only: services come
 * from the booking page's catalogue, slots from the one availability engine,
 * prices from the one pricing service — the same answers the website gets.
 */
@Service
public class IntegrationCatalogService {

    static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final BusinessService businessService;
    private final OpeningHoursService openingHoursService;
    private final BookingPageService bookingPageService;
    private final ServiceCatalogService serviceCatalog;
    private final AvailabilityService availabilityService;
    private final PricingService pricingService;
    private final IntegrationRepository integrationRepository;
    private final AppProperties properties;

    public IntegrationCatalogService(BusinessService businessService,
                                     OpeningHoursService openingHoursService,
                                     BookingPageService bookingPageService,
                                     ServiceCatalogService serviceCatalog,
                                     AvailabilityService availabilityService,
                                     PricingService pricingService,
                                     IntegrationRepository integrationRepository,
                                     AppProperties properties) {
        this.businessService = businessService;
        this.openingHoursService = openingHoursService;
        this.bookingPageService = bookingPageService;
        this.serviceCatalog = serviceCatalog;
        this.availabilityService = availabilityService;
        this.pricingService = pricingService;
        this.integrationRepository = integrationRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public BusinessInfo business(Long businessId) {
        Business business = businessService.requireById(businessId);
        BusinessSettings settings = businessService.settingsOf(businessId);
        List<Channel> channels = integrationRepository.findByBusinessId(businessId).stream()
                .filter(integration -> integration.getProvider() != IntegrationProvider.N8N)
                .map(integration -> new Channel(integration.getProvider().name(), integration.getStatus().name(),
                        integration.getExternalAccountId()))
                .toList();
        return new BusinessInfo(business.getId(), business.getName(), business.getSlug(), business.getDescription(),
                business.getPhone(), business.getEmail(), business.getAddress(), business.getTimezone(),
                business.getCurrency(), bookingPageUrl(business), openingHoursService.intervalsOf(businessId),
                new Policies(settings.getMinimumBookingNoticeMinutes(), settings.getMaximumBookingAdvanceDays(),
                        settings.isAllowCustomerCancellation(), settings.getCancellationDeadlineHours()),
                channels);
    }

    /** Active services, as the booking page lists them. */
    @Transactional(readOnly = true)
    public List<ServiceInfo> services(Long businessId) {
        Business business = businessService.requireById(businessId);
        return bookingPageService.services(business).stream().map(IntegrationCatalogService::toInfo).toList();
    }

    @Transactional(readOnly = true)
    public ServiceInfo service(Long businessId, Long serviceId) {
        Business business = businessService.requireById(businessId);
        return toInfo(bookingPageService.service(business, serviceId));
    }

    /**
     * Bookable start times on one day for a group, with the customer rules
     * (minimum notice, booking window) applied — what the website offers.
     */
    @Transactional(readOnly = true)
    public List<Slot> slots(Long businessId, Long serviceId, LocalDate date, int participants) {
        List<DaySlotsResponse> days = availabilityService.search(businessId,
                new AvailabilityQuery(serviceId, date, date, participants, null), true);
        var zone = businessService.zoneOf(businessId);
        return days.stream()
                .flatMap(day -> day.slots().stream().map(slot -> toSlot(day.date(), slot, participants, zone)))
                .toList();
    }

    /** The first days in a range that have at least one bookable time, soonest first. */
    @Transactional(readOnly = true)
    public List<LocalDate> availableDates(Long businessId, Long serviceId, LocalDate from, LocalDate to,
                                          int participants, int limit) {
        if (to.isBefore(from)) {
            return List.of();
        }
        return availabilityService.search(businessId, new AvailabilityQuery(serviceId, from, to, participants, null),
                        true).stream()
                .filter(day -> !day.slots().isEmpty())
                .map(DaySlotsResponse::date)
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public Quote quote(Long businessId, Long serviceId, int participants, String discountCode) {
        Business business = businessService.requireById(businessId);
        BookableService service = serviceCatalog.requireBookable(businessId, serviceId);
        return toQuote(pricingService.quote(business, service, participants, discountCode));
    }

    public String bookingPageUrl(Business business) {
        return trimSlash(properties.frontendUrl()) + "/b/" + business.getSlug();
    }

    public String manageUrl(String reference) {
        return trimSlash(properties.frontendUrl()) + "/booking/" + reference;
    }

    static Quote toQuote(PriceQuote quote) {
        return new Quote(quote.originalPrice(), quote.discountAmount(), quote.finalPrice(), quote.currency(),
                quote.discountCode(), quote.discountName());
    }

    private static ServiceInfo toInfo(PublicServiceResponse service) {
        return new ServiceInfo(service.id(), service.name(), service.description(), service.category(),
                service.durationMinutes(), service.price(), service.pricePerParticipant(), service.maxParticipants(),
                service.currency(), service.choosableRequirements().stream()
                        .map(requirement -> new ChoosableRequirement(requirement.requirementId(),
                                requirement.resourceTypeName(), requirement.quantity(),
                                requirement.resources().stream()
                                        .map(option -> new NamedResource(option.id(), option.name())).toList()))
                        .toList());
    }

    private static Slot toSlot(LocalDate date, SlotResponse slot, int participants, java.time.ZoneId zone) {
        LocalTime end = LocalTime.ofInstant(slot.endTime(), zone);
        List<ResourceOptions> options = slot.requirements().stream()
                .filter(SlotResponse.SlotRequirement::customerChoosable)
                .map(requirement -> new ResourceOptions(requirement.requirementId(), requirement.resourceTypeName(),
                        choicesOf(requirement, participants)))
                .filter(option -> !option.choices().isEmpty())
                .toList();
        return new Slot(slot.time().format(HH_MM), end.format(HH_MM), LocalDateTime.of(date, slot.time()), options);
    }

    /**
     * Whole, valid answers for one requirement: the engine's combinations for
     * a combinable requirement, otherwise single resources that seat the group.
     * Requirements needing several separately picked resources are left to the
     * automatic assignment — a chat is no place for a multi-select.
     */
    private static List<ResourceChoice> choicesOf(SlotResponse.SlotRequirement requirement, int participants) {
        if (requirement.combinable()) {
            return requirement.allocations().stream()
                    .map(allocation -> new ResourceChoice(allocation.resources().stream()
                            .map(resource -> new NamedResource(resource.id(), resource.name())).toList(),
                            allocation.area()))
                    .toList();
        }
        if (requirement.quantity() != 1) {
            return List.of();
        }
        return requirement.resources().stream()
                .filter(resource -> {
                    Integer room = resource.seatsLeft() != null ? resource.seatsLeft() : resource.capacity();
                    return room == null || room >= participants;
                })
                .map(resource -> new ResourceChoice(List.of(new NamedResource(resource.id(), resource.name())), null))
                .toList();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
