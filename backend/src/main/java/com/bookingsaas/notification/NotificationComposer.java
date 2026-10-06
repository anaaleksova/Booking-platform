package com.bookingsaas.notification;

import com.bookingsaas.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    private static final String FALLBACK_ACCENT = "#2f5bea";

    /** The business's booking-page look, so its emails match it. */
    public record Branding(String accentColor, boolean dark) {
    }

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

    /**
     * A branded HTML version of the message, in the look of the business's
     * booking page: its accent colour, its name, rounded cards, and a black
     * background when the business uses the dark page. Table layout and inline
     * styles only, which is what mail clients render reliably.
     */
    public String html(NotificationType type, ReservationNotificationPayload payload, Branding branding) {
        String accent = branding.accentColor() != null && branding.accentColor().matches("^#[0-9a-fA-F]{6}$")
                ? branding.accentColor() : FALLBACK_ACCENT;
        boolean dark = branding.dark();
        String pageBg = dark ? "#000000" : "#fafafa";
        String cardBg = dark ? "#121214" : "#ffffff";
        String text = dark ? "#f4f5f7" : "#131a2b";
        String muted = dark ? "#a3a8b4" : "#5a6478";
        String line = dark ? "#2a2b30" : "#e6e8ee";
        String onAccent = readableOn(accent);

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Service", payload.serviceName()});
        if (payload.participants() > 1) {
            rows.add(new String[]{"Guests", String.valueOf(payload.participants())});
        }
        if (payload.resourceSummary() != null && !payload.resourceSummary().isBlank()) {
            rows.add(new String[]{"With", payload.resourceSummary()});
        }
        rows.add(new String[]{"Date", dateOf(payload)});
        rows.add(new String[]{"Time", timeRange(payload)});
        rows.add(new String[]{"Customer", payload.customerName()});
        if (payload.businessAddress() != null) {
            rows.add(new String[]{"Address", payload.businessAddress()});
        }
        if (payload.businessPhone() != null) {
            rows.add(new String[]{"Phone", payload.businessPhone()});
        }

        StringBuilder detailRows = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            String border = i == rows.size() - 1 ? "" : "border-bottom:1px solid " + line + ";";
            detailRows.append("<tr><td style=\"padding:12px 0;").append(border)
                    .append("color:").append(muted).append(";font-size:14px;width:34%;vertical-align:top;\">")
                    .append(esc(rows.get(i)[0])).append("</td><td style=\"padding:12px 0;").append(border)
                    .append("color:").append(text).append(";font-size:15px;font-weight:600;vertical-align:top;\">")
                    .append(esc(rows.get(i)[1])).append("</td></tr>");
        }

        String note = payload.detail() == null || payload.detail().isBlank() ? ""
                : "<p style=\"margin:20px 0 0;color:" + muted + ";font-size:15px;line-height:1.6;\">"
                + esc(payload.detail()) + "</p>";

        String button = "";
        if (isCustomerFacing(type) && payload.publicReference() != null) {
            String link = properties.frontendUrl() + "/booking/" + payload.publicReference();
            button = "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:28px 0 4px;\"><tr>"
                    + "<td style=\"background:" + accent + ";border-radius:999px;\">"
                    + "<a href=\"" + esc(link) + "\" style=\"display:inline-block;padding:14px 28px;color:" + onAccent
                    + ";font-size:15px;font-weight:700;text-decoration:none;\">View or manage reservation</a>"
                    + "</td></tr></table>";
        }

        String greeting = isCustomerFacing(type)
                ? "<p style=\"margin:0 0 6px;color:" + muted + ";font-size:15px;\">Hello " + esc(payload.customerName()) + ",</p>"
                : "";

        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"" + (dark ? "dark" : "light") + "\">"
                + "<title>" + esc(subject(type, payload)) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:" + pageBg + ";\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">" + esc(headline(type))
                + " - " + esc(payload.serviceName()) + ", " + esc(dateOf(payload)) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + pageBg
                + ";\"><tr><td align=\"center\" style=\"padding:32px 16px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
                + " style=\"max-width:560px;font-family:'Segoe UI',Inter,Helvetica,Arial,sans-serif;\">"
                // header band in the accent colour
                + "<tr><td style=\"background:" + accent + ";border-radius:24px 24px 0 0;padding:28px 32px;text-align:center;\">"
                + "<div style=\"color:" + onAccent + ";font-size:22px;font-weight:800;letter-spacing:-0.01em;\">"
                + esc(payload.businessName()) + "</div></td></tr>"
                // card
                + "<tr><td style=\"background:" + cardBg + ";border:1px solid " + line
                + ";border-top:0;border-radius:0 0 24px 24px;padding:32px;\">"
                + greeting
                + "<h1 style=\"margin:0 0 20px;color:" + text
                + ";font-size:26px;line-height:1.2;font-weight:800;letter-spacing:-0.02em;\">" + esc(headline(type)) + "</h1>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border:1px solid "
                + line + ";border-radius:16px;\"><tr><td style=\"padding:6px 20px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">" + detailRows
                + "</table></td></tr></table>"
                + note + button
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 8px;text-align:center;color:" + muted + ";font-size:13px;line-height:1.6;\">"
                + esc(payload.businessName())
                + (payload.businessPhone() != null ? " · " + esc(payload.businessPhone()) : "")
                + "</td></tr></table></td></tr></table></body></html>";
    }

    private static String headline(NotificationType type) {
        return switch (type) {
            case RESERVATION_CONFIRMED_CUSTOMER -> "Your reservation is confirmed";
            case RESERVATION_CONFIRMED_BUSINESS -> "New reservation";
            case RESERVATION_CANCELLED_CUSTOMER -> "Your reservation was cancelled";
            case RESERVATION_CANCELLED_BUSINESS -> "Reservation cancelled";
            case RESERVATION_RESCHEDULED_CUSTOMER -> "Your reservation has moved";
            case RESERVATION_REMINDER_CUSTOMER -> "See you soon";
        };
    }

    /** Black or white text, whichever reads better on the given #rrggbb background. */
    private static String readableOn(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance > 0.62 ? "#111214" : "#ffffff";
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
