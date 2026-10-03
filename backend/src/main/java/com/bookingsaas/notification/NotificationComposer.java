package com.bookingsaas.notification;

import com.bookingsaas.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Turns a payload into subject and body text.
 *
 * <p>All times are rendered in the <em>business</em> timezone: a customer in
 * another country must read the time the salon means, not the time their own
 * device would show.
 */
@Component
public class NotificationComposer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final AppProperties properties;

    public NotificationComposer(AppProperties properties) {
        this.properties = properties;
    }

    public String subject(NotificationType type, ReservationNotificationPayload payload) {
        return switch (type) {
            case RESERVATION_CONFIRMED_CUSTOMER -> "Your reservation at " + payload.businessName() + " is confirmed";
            case RESERVATION_CONFIRMED_BUSINESS -> "New reservation: " + payload.serviceName() + " — "
                    + payload.customerName();
            case RESERVATION_CANCELLED_CUSTOMER -> "Your reservation at " + payload.businessName() + " was cancelled";
            case RESERVATION_CANCELLED_BUSINESS -> "Reservation cancelled: " + payload.serviceName() + " — "
                    + payload.customerName();
            case RESERVATION_RESCHEDULED_CUSTOMER -> "Your reservation at " + payload.businessName() + " has moved";
            case RESERVATION_REMINDER_CUSTOMER -> "Reminder: " + payload.serviceName() + " on " + dateOf(payload);
        };
    }

    public String body(NotificationType type, ReservationNotificationPayload payload) {
        StringBuilder body = new StringBuilder();
        body.append(switch (type) {
            case RESERVATION_CONFIRMED_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nYour reservation has been confirmed.\n\n";
            case RESERVATION_CONFIRMED_BUSINESS -> "A new reservation has been received.\n\n";
            case RESERVATION_CANCELLED_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nYour reservation has been cancelled.\n\n";
            case RESERVATION_CANCELLED_BUSINESS -> "A reservation has been cancelled.\n\n";
            case RESERVATION_RESCHEDULED_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nYour reservation has been moved to a new time.\n\n";
            case RESERVATION_REMINDER_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nThis is a reminder about your upcoming reservation.\n\n";
        });

        body.append(details(payload));

        if (payload.detail() != null && !payload.detail().isBlank()) {
            body.append('\n').append(payload.detail()).append('\n');
        }
        if (isCustomerFacing(type) && payload.publicReference() != null) {
            body.append("\nManage this reservation:\n")
                    .append(properties.frontendUrl())
                    .append("/booking/")
                    .append(payload.publicReference())
                    .append('\n');
        }
        body.append("\n— ").append(payload.businessName()).append('\n');
        return body.toString();
    }

    private String details(ReservationNotificationPayload payload) {
        StringBuilder details = new StringBuilder();
        details.append("Service:  ").append(payload.serviceName()).append('\n');
        if (payload.participants() > 1) {
            details.append("Guests:   ").append(payload.participants()).append('\n');
        }
        if (payload.resourceSummary() != null && !payload.resourceSummary().isBlank()) {
            details.append("With:     ").append(payload.resourceSummary()).append('\n');
        }
        details.append("Date:     ").append(dateOf(payload)).append('\n');
        details.append("Time:     ").append(timeRange(payload)).append('\n');
        details.append("Customer: ").append(payload.customerName()).append('\n');
        if (payload.businessAddress() != null) {
            details.append("Address:  ").append(payload.businessAddress()).append('\n');
        }
        if (payload.businessPhone() != null) {
            details.append("Phone:    ").append(payload.businessPhone()).append('\n');
        }
        return details.toString();
    }

    private static boolean isCustomerFacing(NotificationType type) {
        return type != NotificationType.RESERVATION_CONFIRMED_BUSINESS
                && type != NotificationType.RESERVATION_CANCELLED_BUSINESS;
    }

    private static String dateOf(ReservationNotificationPayload payload) {
        return LocalDateTime.ofInstant(payload.start(), payload.zone()).format(DATE);
    }

    private static String timeRange(ReservationNotificationPayload payload) {
        return LocalDateTime.ofInstant(payload.start(), payload.zone()).format(TIME)
                + " - " + LocalDateTime.ofInstant(payload.end(), payload.zone()).format(TIME)
                + " (" + payload.businessTimezone() + ")";
    }
}
