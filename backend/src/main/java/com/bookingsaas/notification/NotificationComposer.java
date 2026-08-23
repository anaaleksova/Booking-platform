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

    public String subject(NotificationType type, BookingNotificationPayload payload) {
        return switch (type) {
            case BOOKING_CONFIRMED_CUSTOMER -> "Your booking at " + payload.businessName() + " is confirmed";
            case BOOKING_CONFIRMED_BUSINESS -> "New booking: " + payload.serviceName() + " — "
                    + payload.customerName();
            case BOOKING_CANCELLED_CUSTOMER -> "Your booking at " + payload.businessName() + " was cancelled";
            case BOOKING_CANCELLED_BUSINESS -> "Booking cancelled: " + payload.serviceName() + " — "
                    + payload.customerName();
            case BOOKING_RESCHEDULED_CUSTOMER -> "Your booking at " + payload.businessName() + " has moved";
            case BOOKING_REMINDER_CUSTOMER -> "Reminder: " + payload.serviceName() + " on " + dateOf(payload);
        };
    }

    public String body(NotificationType type, BookingNotificationPayload payload) {
        StringBuilder body = new StringBuilder();
        body.append(switch (type) {
            case BOOKING_CONFIRMED_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nYour appointment has been confirmed.\n\n";
            case BOOKING_CONFIRMED_BUSINESS -> "A new booking has been received.\n\n";
            case BOOKING_CANCELLED_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nYour appointment has been cancelled.\n\n";
            case BOOKING_CANCELLED_BUSINESS -> "A booking has been cancelled.\n\n";
            case BOOKING_RESCHEDULED_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nYour appointment has been moved to a new time.\n\n";
            case BOOKING_REMINDER_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nThis is a reminder about your upcoming appointment.\n\n";
        });

        body.append(details(payload));

        if (payload.detail() != null && !payload.detail().isBlank()) {
            body.append('\n').append(payload.detail()).append('\n');
        }
        if (isCustomerFacing(type) && payload.publicReference() != null) {
            body.append("\nManage this booking:\n")
                    .append(properties.frontendUrl())
                    .append("/booking/")
                    .append(payload.publicReference())
                    .append('\n');
        }
        body.append("\n— ").append(payload.businessName()).append('\n');
        return body.toString();
    }

    private String details(BookingNotificationPayload payload) {
        StringBuilder details = new StringBuilder();
        details.append("Service:  ").append(payload.serviceName()).append('\n');
        if (payload.staffName() != null) {
            details.append("With:     ").append(payload.staffName()).append('\n');
        }
        if (payload.resourceName() != null) {
            details.append("Resource: ").append(payload.resourceName()).append('\n');
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
        return type != NotificationType.BOOKING_CONFIRMED_BUSINESS
                && type != NotificationType.BOOKING_CANCELLED_BUSINESS;
    }

    private static String dateOf(BookingNotificationPayload payload) {
        return LocalDateTime.ofInstant(payload.start(), payload.zone()).format(DATE);
    }

    private static String timeRange(BookingNotificationPayload payload) {
        return LocalDateTime.ofInstant(payload.start(), payload.zone()).format(TIME)
                + " - " + LocalDateTime.ofInstant(payload.end(), payload.zone()).format(TIME)
                + " (" + payload.businessTimezone() + ")";
    }
}
