package com.bookingsaas.event;

import com.bookingsaas.availability.AvailabilityQuery;
import com.bookingsaas.availability.AvailabilityService;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.event.dto.EventRequest;
import com.bookingsaas.event.dto.EventResponse;
import com.bookingsaas.event.dto.PublicEventResponse;
import com.bookingsaas.media.Media;
import com.bookingsaas.media.MediaService;
import com.bookingsaas.service.BookableService;
import com.bookingsaas.service.ServiceCatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Events on the booking page: the owner's list, and what customers (and the
 * chat) see today — with "fully booked" worked out by the same availability
 * search the booking wizard uses, so the two can never disagree.
 */
@Service
public class EventService {

    private final BusinessEventRepository repository;
    private final ServiceCatalogService serviceCatalog;
    private final AvailabilityService availabilityService;
    private final BusinessService businessService;
    private final MediaService mediaService;
    private final CurrentTenant currentTenant;
    private final Clock clock;

    public EventService(BusinessEventRepository repository,
                        ServiceCatalogService serviceCatalog,
                        AvailabilityService availabilityService,
                        BusinessService businessService,
                        MediaService mediaService,
                        CurrentTenant currentTenant,
                        Clock clock) {
        this.repository = repository;
        this.serviceCatalog = serviceCatalog;
        this.availabilityService = availabilityService;
        this.businessService = businessService;
        this.mediaService = mediaService;
        this.currentTenant = currentTenant;
        this.clock = clock;
    }

    // --- customer-facing ---------------------------------------------------

    /** The events on the booking page right now, soonest first. */
    @Transactional(readOnly = true)
    public List<PublicEventResponse> showing(Business business) {
        ZoneId zone = business.zoneId();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        List<BusinessEvent> events = repository
                .findByBusinessIdAndShowFromLessThanEqualAndShowUntilGreaterThanEqualOrderByDateAscStartTimeAsc(
                        business.getId(), today, today)
                .stream()
                .filter(event -> event.showingAt(now, zone))
                .toList();
        if (events.isEmpty()) {
            return List.of();
        }
        // Only active services are bookable; looked up rather than required, so an event whose
        // service was switched off is simply not bookable instead of failing the whole page.
        Map<Long, BookableService> bookable = serviceCatalog.activeServicesOf(business.getId()).stream()
                .collect(Collectors.toMap(BookableService::getId, Function.identity()));
        return events.stream()
                .map(event -> {
                    BookableService service = event.getServiceId() == null ? null : bookable.get(event.getServiceId());
                    return new PublicEventResponse(event.getId(), event.getTitle(), event.getDescription(),
                            event.getPerformer(), Media.urlOf(event.getImageMediaId()), event.getDate(),
                            event.getStartTime(), event.getEndTime(), service == null ? null : service.getId(),
                            booking(business, event, service, now));
                })
                .toList();
    }

    /**
     * OPEN while a customer can still book a time during the event. FULL only
     * when there is no room left at all; when there is room but the booking
     * rules keep customers out for now (too far ahead, too short notice), the
     * event is NONE rather than wrongly "fully booked".
     */
    private String booking(Business business, BusinessEvent event, BookableService service, Instant now) {
        if (service == null || event.startedAt(now, business.zoneId())) {
            return "NONE";
        }
        if (hasRoom(business, event, service, true)) {
            return "OPEN";
        }
        return hasRoom(business, event, service, false) ? "NONE" : "FULL";
    }

    /**
     * Whether a group of some size fits at a time during the event. Every size
     * is tried, smallest first: a table for six may be free when no table takes
     * one person.
     *
     * @param customerFacing true applies the customer booking rules (notice, advance)
     */
    private boolean hasRoom(Business business, BusinessEvent event, BookableService service, boolean customerFacing) {
        ZoneId zone = business.zoneId();
        Instant start = event.startsAt(zone);
        Instant end = event.endsAt(zone);
        for (int people = 1; people <= service.getMaxParticipants(); people++) {
            boolean free = availabilityService.search(business.getId(),
                            new AvailabilityQuery(service.getId(), event.getDate(), event.getDate(), people, null),
                            customerFacing)
                    .stream()
                    .flatMap(day -> day.slots().stream())
                    .anyMatch(slot -> !slot.startTime().isBefore(start) && slot.startTime().isBefore(end));
            if (free) {
                return true;
            }
        }
        return false;
    }

    // --- admin API ---------------------------------------------------------

    /** Newest first, past events included. */
    @Transactional(readOnly = true)
    public List<EventResponse> list() {
        Long businessId = currentTenant.businessId();
        ZoneId zone = businessService.zoneOf(businessId);
        Instant now = clock.instant();
        return repository.findByBusinessIdOrderByDateDescStartTimeDesc(businessId).stream()
                .map(event -> EventResponse.from(event, now, zone))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long id) {
        Long businessId = currentTenant.businessId();
        return EventResponse.from(require(businessId, id), clock.instant(), businessService.zoneOf(businessId));
    }

    @Transactional
    public EventResponse create(EventRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        BusinessEvent event = new BusinessEvent();
        event.setBusinessId(businessId);
        apply(businessId, event, request);
        return EventResponse.from(repository.save(event), clock.instant(), businessService.zoneOf(businessId));
    }

    @Transactional
    public EventResponse update(Long id, EventRequest request) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        BusinessEvent event = require(businessId, id);
        UUID previousImage = event.getImageMediaId();
        apply(businessId, event, request);
        BusinessEvent saved = repository.save(event);
        mediaService.discardIfReplaced(businessId, previousImage, saved.getImageMediaId());
        return EventResponse.from(saved, clock.instant(), businessService.zoneOf(businessId));
    }

    /** Removes the event and its picture. Reservations made for it are untouched. */
    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        BusinessEvent event = require(businessId, id);
        repository.delete(event);
        repository.flush();
        mediaService.discardIfReplaced(businessId, event.getImageMediaId(), null);
    }

    // --- internals ---------------------------------------------------------

    private BusinessEvent require(Long businessId, Long id) {
        return repository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> ApiException.notFound("Event", id));
    }

    private void apply(Long businessId, BusinessEvent event, EventRequest request) {
        LocalDate showFrom = request.showFrom() != null ? request.showFrom()
                : request.date().minusDays(BusinessEvent.DEFAULT_SHOW_DAYS_BEFORE);
        LocalDate showUntil = request.showUntil() != null ? request.showUntil() : request.date();
        if (showUntil.isAfter(request.date())) {
            throw ApiException.validation("The event can be shown until its own day at the latest.");
        }
        if (showFrom.isAfter(showUntil)) {
            throw ApiException.validation("The first day on the page must not be after the last day.");
        }
        if (request.endTime() != null && request.endTime().equals(request.startTime())) {
            throw ApiException.validation("The end time must differ from the start time.");
        }
        if (request.serviceId() != null) {
            serviceCatalog.requireForBusiness(businessId, request.serviceId());
        }

        event.setTitle(request.title().trim());
        event.setDescription(clean(request.description()));
        event.setPerformer(clean(request.performer()));
        event.setImageMediaId(mediaService.requireOwned(businessId, request.imageMediaId()));
        event.setDate(request.date());
        event.setStartTime(request.startTime());
        event.setEndTime(request.endTime());
        event.setShowFrom(showFrom);
        event.setShowUntil(showUntil);
        event.setServiceId(request.serviceId());
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
