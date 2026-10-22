package com.bookingsaas.notification;

import com.bookingsaas.common.calendar.CalendarLinks;
import com.bookingsaas.common.calendar.CustomerEvent;
import com.bookingsaas.common.calendar.Ics;
import com.bookingsaas.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

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
                    + payload.customerName() + viaChannel(payload);
            case RESERVATION_CANCELLED_CUSTOMER -> "Your reservation at " + payload.businessName() + " was cancelled";
            case RESERVATION_CANCELLED_BUSINESS -> "Reservation cancelled: " + payload.serviceName() + " — "
                    + payload.customerName();
            case RESERVATION_RESCHEDULED_CUSTOMER -> "Your reservation at " + payload.businessName() + " has moved";
            case RESERVATION_RESCHEDULED_BUSINESS -> "Reservation moved: " + payload.serviceName() + " — "
                    + payload.customerName();
            case RESERVATION_REMINDER_CUSTOMER -> "Reminder: " + payload.serviceName() + " on " + dateOf(payload);
            case RESERVATION_CONFIRMED_STAFF -> "New appointment: " + payload.serviceName() + " — "
                    + payload.customerName() + ", " + shortWhen(payload);
            case RESERVATION_RESCHEDULED_STAFF -> "Appointment moved: " + payload.serviceName() + " — "
                    + payload.customerName() + ", now " + shortWhen(payload);
            case RESERVATION_CANCELLED_STAFF -> "Appointment cancelled: " + payload.serviceName() + " — "
                    + payload.customerName() + ", " + shortWhen(payload);
            case RESERVATION_UNASSIGNED_STAFF -> "Removed from your schedule: " + payload.serviceName() + " — "
                    + payload.customerName() + ", " + shortWhen(payload);
        };
    }

    /** "Tue 21 Oct at 14:00", in the business timezone — what fits in a subject line. */
    private static String shortWhen(ReservationNotificationPayload payload) {
        LocalDateTime start = LocalDateTime.ofInstant(payload.start(), payload.zone());
        return start.format(SHORT_DATE) + " at " + start.format(TIME);
    }

    /** " (via Instagram)" for bookings from a social channel, where the owner may not be looking. */
    private static String viaChannel(ReservationNotificationPayload payload) {
        return "INSTAGRAM".equals(payload.source()) || "FACEBOOK".equals(payload.source())
                ? " (via " + sourceLabel(payload.source()) + ")" : "";
    }

    public String body(NotificationType type, ReservationNotificationPayload payload) {
        return body(type, payload, null);
    }

    /** @param staff the employee a staff message is for; null otherwise */
    public String body(NotificationType type, ReservationNotificationPayload payload, StaffMember staff) {
        StringBuilder body = new StringBuilder();
        if (staff != null) {
            body.append("Hello ").append(staff.name()).append(",\n\n");
        }
        body.append(switch (type) {
            case RESERVATION_CONFIRMED_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nYour reservation has been confirmed.\n\n";
            case RESERVATION_CONFIRMED_BUSINESS -> "A new reservation has been received.\n\n";
            case RESERVATION_CANCELLED_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nYour reservation has been cancelled.\n\n";
            case RESERVATION_CANCELLED_BUSINESS -> "A reservation has been cancelled.\n\n";
            case RESERVATION_RESCHEDULED_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nYour reservation has been moved to a new time.\n\n";
            case RESERVATION_RESCHEDULED_BUSINESS -> "A reservation has been moved to a new time.\n\n";
            case RESERVATION_REMINDER_CUSTOMER -> "Hello " + payload.customerName()
                    + ",\n\nThis is a reminder about your upcoming reservation.\n\n";
            case RESERVATION_CONFIRMED_STAFF -> "You have a new appointment.\n\n";
            case RESERVATION_RESCHEDULED_STAFF -> "One of your appointments has moved to a new time.\n\n";
            case RESERVATION_CANCELLED_STAFF -> "One of your appointments has been cancelled.\n\n";
            case RESERVATION_UNASSIGNED_STAFF -> "This appointment has been given to someone else, "
                    + "so it is no longer on your schedule.\n\n";
        });

        for (String[] row : rows(type, payload)) {
            body.append(String.format("%-12s", row[0] + ":")).append(row[1]).append('\n');
        }

        if (payload.detail() != null && !payload.detail().isBlank()) {
            body.append('\n').append(payload.detail()).append('\n');
        }
        if (isCustomerFacing(type) && payload.publicReference() != null) {
            body.append("\nManage this reservation:\n")
                    .append(properties.frontendUrl())
                    .append("/booking/")
                    .append(payload.publicReference())
                    .append('\n');
            if (offersCalendar(type)) {
                Ics.Event event = customerEvent(payload);
                body.append("\nAdd it to your calendar:\n")
                        .append("Google: ").append(CalendarLinks.google(event)).append('\n')
                        .append("Outlook: ").append(CalendarLinks.outlook(event)).append('\n')
                        .append("Apple / other: ")
                        .append(CustomerEvent.fileUrl(properties.frontendUrl(), payload.publicReference()))
                        .append('\n');
            }
        } else if (!isCustomerFacing(type)) {
            body.append(type.isForStaff() ? "\nYour reservations:\n" : "\nOpen reservations:\n")
                    .append(properties.frontendUrl()).append("/admin/reservations\n");
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
        return html(type, payload, branding, null);
    }

    /** @param staff the employee a staff message is for; null otherwise */
    public String html(NotificationType type, ReservationNotificationPayload payload, Branding branding,
                       StaffMember staff) {
        String accent = branding.accentColor() != null && branding.accentColor().matches("^#[0-9a-fA-F]{6}$")
                ? branding.accentColor() : FALLBACK_ACCENT;
        boolean dark = branding.dark();
        String pageBg = dark ? "#000000" : "#fafafa";
        String cardBg = dark ? "#121214" : "#ffffff";
        String text = dark ? "#f4f5f7" : "#131a2b";
        String muted = dark ? "#a3a8b4" : "#5a6478";
        String line = dark ? "#2a2b30" : "#e6e8ee";
        String onAccent = readableOn(accent);

        List<String[]> rows = rows(type, payload);

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
        String link = isCustomerFacing(type)
                ? (payload.publicReference() == null ? null : properties.frontendUrl() + "/booking/" + payload.publicReference())
                : properties.frontendUrl() + "/admin/reservations";
        if (link != null) {
            String label = isCustomerFacing(type) ? "View or manage reservation"
                    : type.isForStaff() ? "Open my reservations" : "Open reservations";
            button = "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:28px 0 4px;\"><tr>"
                    + "<td style=\"background:" + accent + ";border-radius:999px;\">"
                    + "<a href=\"" + esc(link) + "\" style=\"display:inline-block;padding:14px 28px;color:" + onAccent
                    + ";font-size:15px;font-weight:700;text-decoration:none;\">" + label + "</a>"
                    + "</td></tr></table>";
        }

        // "Add to calendar": each link opens the customer's own calendar with the event
        // filled in, and nothing is added until they save it there.
        String calendar = "";
        if (offersCalendar(type) && payload.publicReference() != null) {
            Ics.Event event = customerEvent(payload);
            String pill = "display:inline-block;margin:0 8px 8px 0;padding:9px 16px;border:1px solid " + line
                    + ";border-radius:999px;color:" + text + ";font-size:14px;font-weight:600;text-decoration:none;";
            calendar = "<p style=\"margin:24px 0 10px;color:" + muted + ";font-size:14px;\">Add it to your calendar</p>"
                    + "<div>"
                    + "<a href=\"" + esc(CalendarLinks.google(event)) + "\" style=\"" + pill + "\">Google</a>"
                    + "<a href=\"" + esc(CalendarLinks.outlook(event)) + "\" style=\"" + pill + "\">Outlook</a>"
                    + "<a href=\"" + esc(CustomerEvent.fileUrl(properties.frontendUrl(), payload.publicReference()))
                    + "\" style=\"" + pill + "\">Apple / other</a>"
                    + "</div>";
        }

        String greetingName = staff != null ? staff.name() : isCustomerFacing(type) ? payload.customerName() : null;
        String greeting = greetingName != null
                ? "<p style=\"margin:0 0 6px;color:" + muted + ";font-size:15px;\">Hello " + esc(greetingName) + ",</p>"
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
                + note + button + calendar
                + "</td></tr>"
                + "<tr><td style=\"padding:20px 8px;text-align:center;color:" + muted + ";font-size:13px;line-height:1.6;\">"
                + esc(payload.businessName())
                + (payload.businessPhone() != null ? " · " + esc(payload.businessPhone()) : "")
                + "</td></tr></table></td></tr></table></body></html>";
    }

    /**
     * The calendar part of a message, or null for messages that carry none.
     *
     * <ul>
     *   <li>Customer confirmation and move: a plain file, added only if the
     *       customer chooses to.</li>
     *   <li>The business's new, moved and cancelled messages: an invitation,
     *       which the owner's mail app adds to (moves in, removes from) their
     *       calendar by itself.</li>
     * </ul>
     *
     * The sequence is the current time, so each message about a reservation
     * outranks the one before it.
     */
    public String calendarFile(NotificationType type, ReservationNotificationPayload payload, String recipient,
                               Instant now) {
        return calendarFile(type, payload, recipient, now, null);
    }

    /**
     * As {@link #calendarFile(NotificationType, ReservationNotificationPayload, String, Instant)}; a staff
     * message is an invitation to the employee, so the appointment is in their own calendar too — and
     * leaves it when cancelled or given to someone else.
     */
    public String calendarFile(NotificationType type, ReservationNotificationPayload payload, String recipient,
                               Instant now, StaffMember staff) {
        if (payload.publicReference() == null) {
            return null;
        }
        long sequence = now.getEpochSecond();
        if (type.isForStaff()) {
            if (staff == null) {
                return null;
            }
            Ics.Method method = type == NotificationType.RESERVATION_CANCELLED_STAFF
                    || type == NotificationType.RESERVATION_UNASSIGNED_STAFF ? Ics.Method.CANCEL : Ics.Method.REQUEST;
            Ics.Event owner = ownerEvent(payload, sequence);
            // One event per employee, apart from the owner's own.
            Ics.Event event = new Ics.Event(payload.publicReference() + "-staff-" + staff.employeeId()
                    + "@booking-platform", owner.start(), owner.end(), owner.summary(), owner.description(),
                    owner.location(), owner.url(), false, sequence);
            return Ics.invitation(method, event, properties.notifications().fromAddress(), payload.businessName(),
                    recipient, now);
        }
        return switch (type) {
            case RESERVATION_CONFIRMED_CUSTOMER, RESERVATION_RESCHEDULED_CUSTOMER ->
                    Ics.calendar(List.of(versioned(customerEvent(payload), sequence)), now);
            case RESERVATION_CONFIRMED_BUSINESS, RESERVATION_RESCHEDULED_BUSINESS ->
                    Ics.invitation(Ics.Method.REQUEST, ownerEvent(payload, sequence),
                            properties.notifications().fromAddress(), payload.businessName(), recipient, now);
            case RESERVATION_CANCELLED_BUSINESS ->
                    Ics.invitation(Ics.Method.CANCEL, ownerEvent(payload, sequence),
                            properties.notifications().fromAddress(), payload.businessName(), recipient, now);
            default -> null;
        };
    }

    /** The reservation in the owner's calendar: who, what, and how to reach them. */
    private Ics.Event ownerEvent(ReservationNotificationPayload payload, long sequence) {
        List<String> lines = new ArrayList<>();
        lines.add("Customer: " + payload.customerName());
        if (hasText(payload.customerPhone())) {
            lines.add("Phone: " + payload.customerPhone());
        }
        if (hasText(payload.customerEmail())) {
            lines.add("Email: " + payload.customerEmail());
        }
        if (payload.participants() > 1) {
            lines.add("Guests: " + payload.participants());
        }
        if (hasText(payload.resourceSummary())) {
            lines.add("With: " + payload.resourceSummary());
        }
        if (hasText(payload.source())) {
            lines.add("Booked via: " + sourceLabel(payload.source()));
        }
        String link = properties.frontendUrl() + "/admin/reservations";
        lines.add("");
        lines.add(link);
        String guests = payload.participants() > 1 ? " (" + payload.participants() + ")" : "";
        // Its own uid: if the owner books with their own address, the two events stay apart.
        return new Ics.Event(payload.publicReference() + "-business@booking-platform", payload.start(), payload.end(),
                payload.serviceName() + " · " + payload.customerName() + guests, String.join("\n", lines),
                payload.resourceSummary(), link, false, sequence);
    }

    private static Ics.Event versioned(Ics.Event event, long sequence) {
        return new Ics.Event(event.uid(), event.start(), event.end(), event.summary(), event.description(),
                event.location(), event.url(), event.cancelled(), sequence);
    }

    private Ics.Event customerEvent(ReservationNotificationPayload payload) {
        return CustomerEvent.of(payload.publicReference(), payload.businessName(), payload.businessAddress(),
                payload.businessPhone(), payload.serviceName(), payload.resourceSummary(), payload.start(),
                payload.end(), false, properties.frontendUrl());
    }

    /** Messages about a reservation that still stands: they offer to add it to the customer's calendar. */
    private static boolean offersCalendar(NotificationType type) {
        return type == NotificationType.RESERVATION_CONFIRMED_CUSTOMER
                || type == NotificationType.RESERVATION_RESCHEDULED_CUSTOMER
                || type == NotificationType.RESERVATION_REMINDER_CUSTOMER;
    }

    private static String headline(NotificationType type) {
        return switch (type) {
            case RESERVATION_CONFIRMED_CUSTOMER -> "Your reservation is confirmed";
            case RESERVATION_CONFIRMED_BUSINESS -> "New reservation";
            case RESERVATION_CANCELLED_CUSTOMER -> "Your reservation was cancelled";
            case RESERVATION_CANCELLED_BUSINESS -> "Reservation cancelled";
            case RESERVATION_RESCHEDULED_CUSTOMER -> "Your reservation has moved";
            case RESERVATION_RESCHEDULED_BUSINESS -> "Reservation moved";
            case RESERVATION_CONFIRMED_STAFF -> "New appointment";
            case RESERVATION_RESCHEDULED_STAFF -> "Appointment moved";
            case RESERVATION_CANCELLED_STAFF -> "Appointment cancelled";
            case RESERVATION_UNASSIGNED_STAFF -> "No longer on your schedule";
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

    /**
     * The details table, shared by the text and HTML versions. The customer gets
     * where to go; the business gets who to call and where the booking came from.
     */
    private static List<String[]> rows(NotificationType type, ReservationNotificationPayload payload) {
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
        if (isCustomerFacing(type)) {
            if (payload.businessAddress() != null) {
                rows.add(new String[]{"Address", payload.businessAddress()});
            }
            if (payload.businessPhone() != null) {
                rows.add(new String[]{"Phone", payload.businessPhone()});
            }
        } else {
            if (hasText(payload.customerPhone())) {
                rows.add(new String[]{"Phone", payload.customerPhone()});
            }
            if (hasText(payload.customerEmail())) {
                rows.add(new String[]{"Email", payload.customerEmail()});
            }
            if (hasText(payload.source())) {
                rows.add(new String[]{"Booked via", sourceLabel(payload.source())});
            }
        }
        return rows;
    }

    private static String sourceLabel(String source) {
        return switch (source) {
            case "WEB" -> "Booking website";
            case "ADMIN" -> "Admin panel";
            case "INSTAGRAM" -> "Instagram";
            case "FACEBOOK" -> "Facebook Messenger";
            case "API" -> "API";
            default -> source.charAt(0) + source.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isCustomerFacing(NotificationType type) {
        return type != NotificationType.RESERVATION_CONFIRMED_BUSINESS
                && type != NotificationType.RESERVATION_CANCELLED_BUSINESS
                && type != NotificationType.RESERVATION_RESCHEDULED_BUSINESS
                && !type.isForStaff();
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
