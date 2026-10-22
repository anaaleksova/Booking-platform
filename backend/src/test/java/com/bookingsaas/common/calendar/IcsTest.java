package com.bookingsaas.common.calendar;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IcsTest {

    private static final Instant START = Instant.parse("2026-10-19T12:00:00Z");

    private static Ics.Event event(String summary) {
        return new Ics.Event("ref-1@booking-platform", START, START.plusSeconds(3600), summary, "Line one\nLine two",
                "Main st 1", null, false, 7);
    }

    @Test
    void writes_an_event_in_utc_with_escaped_text_and_crlf_lines() {
        String ics = Ics.calendar(List.of(event("Massage, deep; tissue")), START);

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n")
                .contains("METHOD:PUBLISH\r\n")
                .contains("DTSTART:20261019T120000Z\r\n", "DTEND:20261019T130000Z\r\n")
                .contains("SUMMARY:Massage\\, deep\\; tissue\r\n")
                .contains("DESCRIPTION:Line one\\nLine two\r\n")
                .contains("SEQUENCE:7\r\n", "STATUS:CONFIRMED\r\n")
                .doesNotContain("ORGANIZER", "ATTENDEE");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void an_invitation_names_the_sender_and_an_attendee_who_has_already_accepted() {
        // Unfolded, as a calendar reads it: the attendee line is longer than one 75-octet line.
        String ics = Ics.invitation(Ics.Method.REQUEST, event("Massage"), "no-reply@app.test", "Serenity, Spa",
                "owner@spa.test", START).replace("\r\n ", "");

        assertThat(ics).contains("METHOD:REQUEST\r\n", "STATUS:CONFIRMED\r\n",
                "ORGANIZER;CN=\"Serenity, Spa\":mailto:no-reply@app.test\r\n",
                "ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=FALSE:mailto:owner@spa.test\r\n");
        assertThat(Ics.methodOf(ics)).isEqualTo("REQUEST");

        String cancel = Ics.invitation(Ics.Method.CANCEL, event("Massage"), "no-reply@app.test", null,
                "owner@spa.test", START);
        assertThat(cancel).contains("METHOD:CANCEL\r\n", "STATUS:CANCELLED\r\n", "UID:ref-1@booking-platform\r\n");
    }

    @Test
    void long_lines_are_folded_at_75_octets_without_splitting_a_character() {
        String summary = "Масажа за двајца ".repeat(10);
        String ics = Ics.calendar(List.of(event(summary)), START);

        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        // Unfolding gives the text back exactly.
        assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:" + summary);
    }

    @Test
    void add_to_calendar_links_carry_the_event() {
        Ics.Event event = new Ics.Event("u", START, START.plusSeconds(3600), "Massage at Spa", "With: Anna",
                "Main st 1", null, false, 0);

        assertThat(CalendarLinks.google(event)).startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE")
                .contains("text=Massage%20at%20Spa", "dates=20261019T120000Z/20261019T130000Z",
                        "location=Main%20st%201");
        assertThat(CalendarLinks.outlook(event)).contains("subject=Massage%20at%20Spa",
                "startdt=2026-10-19T12:00:00Z", "enddt=2026-10-19T13:00:00Z");
    }
}
