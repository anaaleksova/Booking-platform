package com.bookingsaas.event;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Something happening at the business — a live band on Friday, a wine
 * tasting — shown on the booking page from {@code showFrom} to
 * {@code showUntil} and, with a service, bookable from there.
 *
 * <p>Dates and times are wall-clock in the business timezone, like opening
 * hours: "Friday 21:00" stays Friday 21:00 across a daylight-saving change.
 */
@Entity
@Table(name = "business_event")
@Getter
@Setter
@NoArgsConstructor
public class BusinessEvent extends Auditable {

    /** How long before the event it appears on the page when the owner does not say. */
    public static final int DEFAULT_SHOW_DAYS_BEFORE = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(length = 160)
    private String performer;

    @Column(name = "image_media_id")
    private UUID imageMediaId;

    @Column(name = "event_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /** Null when open-ended; earlier than the start when the event runs past midnight. */
    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "show_from", nullable = false)
    private LocalDate showFrom;

    @Column(name = "show_until", nullable = false)
    private LocalDate showUntil;

    @Column(name = "service_id")
    private Long serviceId;

    public Instant startsAt(ZoneId zone) {
        return date.atTime(startTime).atZone(zone).toInstant();
    }

    /** The end, or the end of the event's day when no end time is set. */
    public Instant endsAt(ZoneId zone) {
        if (endTime == null) {
            return date.plusDays(1).atStartOfDay(zone).toInstant();
        }
        LocalDate endDate = endTime.isAfter(startTime) ? date : date.plusDays(1);
        return endDate.atTime(endTime).atZone(zone).toInstant();
    }

    /** On the booking page now: inside its display window and not over yet. */
    public boolean showingAt(Instant now, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        return !today.isBefore(showFrom) && !today.isAfter(showUntil) && now.isBefore(endsAt(zone));
    }

    public boolean startedAt(Instant now, ZoneId zone) {
        return !now.isBefore(startsAt(zone));
    }
}
