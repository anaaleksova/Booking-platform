package com.bookingsaas.common.calendar;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * "Add to calendar" links. Each opens the person's own calendar with the event
 * filled in; nothing is added until they press save there, so the customer
 * always decides.
 */
public final class CalendarLinks {

    private static final DateTimeFormatter GOOGLE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private CalendarLinks() {
    }

    public static String google(Ics.Event event) {
        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + encode(event.summary())
                + "&dates=" + GOOGLE.format(event.start()) + "/" + GOOGLE.format(event.end())
                + "&details=" + encode(event.description())
                + "&location=" + encode(event.location());
    }

    public static String outlook(Ics.Event event) {
        return "https://outlook.live.com/calendar/0/deeplink/compose?path=%2Fcalendar%2Faction%2Fcompose&rru=addevent"
                + "&subject=" + encode(event.summary())
                + "&startdt=" + ISO.format(event.start())
                + "&enddt=" + ISO.format(event.end())
                + "&body=" + encode(event.description())
                + "&location=" + encode(event.location());
    }

    private static String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
