package com.bookingsaas.common.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes iCalendar (RFC 5545) text: the format every calendar app reads —
 * Google, Apple, Outlook.
 *
 * <p>Times are written in UTC ({@code ...Z}), which every client converts to
 * its own zone, so no {@code VTIMEZONE} block is needed.
 */
public final class Ics {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final int MAX_LINE_OCTETS = 75;

    private Ics() {
    }

    /**
     * One event.
     *
     * @param uid      stable across updates, so a changed reservation replaces its event
     * @param sequence grows with every change; calendars keep the highest
     */
    public record Event(String uid, Instant start, Instant end, String summary, String description,
                        String location, String url, boolean cancelled, long sequence) {
    }

    /** What an invitation asks of the recipient's calendar. */
    public enum Method {
        /** Add the event, or update it when the uid is already there. */
        REQUEST,
        /** Remove the event. */
        CANCEL
    }

    /** A plain calendar file: imported when the person chooses to add it. */
    public static String calendar(List<Event> events, Instant now) {
        StringBuilder out = header("PUBLISH");
        for (Event event : events) {
            event(out, event, null, null, null, now);
        }
        return footer(out);
    }

    /**
     * An invitation, which mail apps put straight into the recipient's
     * calendar. The recipient is listed as having already accepted, so
     * calendars have nothing to ask them.
     *
     * @param organizer the address the email comes from
     */
    public static String invitation(Method method, Event event, String organizer, String organizerName,
                                    String attendee, Instant now) {
        StringBuilder out = header(method.name());
        event(out, method == Method.CANCEL ? cancelled(event) : event, organizer, organizerName, attendee, now);
        return footer(out);
    }

    /** The METHOD of an .ics text, or null — what the email needs to label the invitation. */
    public static String methodOf(String ics) {
        for (String line : ics.split("\r\n")) {
            if (line.startsWith("METHOD:")) {
                return line.substring("METHOD:".length());
            }
        }
        return null;
    }

    private static StringBuilder header(String method) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//Booking Platform//Reservations//EN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:" + method);
        return out;
    }

    private static String footer(StringBuilder out) {
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    private static void event(StringBuilder out, Event event, String organizer, String organizerName,
                              String attendee, Instant now) {
        line(out, "BEGIN:VEVENT");
        line(out, "UID:" + text(event.uid()));
        line(out, "DTSTAMP:" + UTC.format(now));
        line(out, "SEQUENCE:" + event.sequence());
        line(out, "DTSTART:" + UTC.format(event.start()));
        line(out, "DTEND:" + UTC.format(event.end()));
        line(out, "SUMMARY:" + text(event.summary()));
        if (event.location() != null && !event.location().isBlank()) {
            line(out, "LOCATION:" + text(event.location()));
        }
        if (event.description() != null && !event.description().isBlank()) {
            line(out, "DESCRIPTION:" + text(event.description()));
        }
        if (event.url() != null) {
            line(out, "URL:" + event.url());
        }
        if (organizer != null) {
            line(out, "ORGANIZER" + (organizerName == null ? "" : ";CN=" + param(organizerName))
                    + ":mailto:" + organizer);
        }
        if (attendee != null) {
            line(out, "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=FALSE:mailto:" + attendee);
        }
        line(out, "STATUS:" + (event.cancelled() ? "CANCELLED" : "CONFIRMED"));
        line(out, "TRANSP:OPAQUE");
        line(out, "END:VEVENT");
    }

    private static Event cancelled(Event event) {
        return new Event(event.uid(), event.start(), event.end(), event.summary(), event.description(),
                event.location(), event.url(), true, event.sequence());
    }

    /** A TEXT value: backslash, comma, semicolon and line breaks escaped. */
    static String text(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** A parameter value (e.g. a name): quoted, since it may hold commas or colons. */
    private static String param(String value) {
        return "\"" + value.replace("\"", "'").replace("\r", " ").replace("\n", " ") + "\"";
    }

    /** Appends a content line, folded at 75 octets as the format requires, ended with CRLF. */
    private static void line(StringBuilder out, String content) {
        int octets = 0;
        int i = 0;
        while (i < content.length()) {
            int codePoint = content.codePointAt(i);
            int size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > MAX_LINE_OCTETS) {
                out.append("\r\n ");
                octets = 1;                         // the leading space of the continuation
            }
            out.appendCodePoint(codePoint);
            octets += size;
            i += Character.charCount(codePoint);
        }
        out.append("\r\n");
    }
}
