package com.bookingsaas.event.dto;

import com.bookingsaas.event.BusinessEvent;
import com.bookingsaas.media.Media;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * An event as the owner manages it.
 *
 * @param state UPCOMING (not on the page yet), SHOWING or PAST — derived for display
 */
public record EventResponse(
        Long id,
        String title,
        String description,
        String performer,
        UUID imageMediaId,
        String imageUrl,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate showFrom,
        LocalDate showUntil,
        Long serviceId,
        String state,
        Set<String> sharedOn
) {

    /** @param sharedOn the platforms (FACEBOOK, INSTAGRAM) it has been posted on */
    public static EventResponse from(BusinessEvent event, Instant now, ZoneId zone, Set<String> sharedOn) {
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getPerformer(),
                event.getImageMediaId(), Media.urlOf(event.getImageMediaId()), event.getDate(),
                event.getStartTime(), event.getEndTime(), event.getShowFrom(), event.getShowUntil(),
                event.getServiceId(), stateOf(event, now, zone), sharedOn);
    }

    private static String stateOf(BusinessEvent event, Instant now, ZoneId zone) {
        if (event.showingAt(now, zone)) {
            return "SHOWING";
        }
        return LocalDate.ofInstant(now, zone).isBefore(event.getShowFrom()) ? "UPCOMING" : "PAST";
    }
}
