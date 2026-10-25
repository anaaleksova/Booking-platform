package com.bookingsaas.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("event timing")
class BusinessEventTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Skopje");
    private static final LocalDate DAY = LocalDate.of(2030, 5, 10);

    private static BusinessEvent event(String start, String end) {
        BusinessEvent event = new BusinessEvent();
        event.setDate(DAY);
        event.setStartTime(LocalTime.parse(start));
        event.setEndTime(end == null ? null : LocalTime.parse(end));
        event.setShowFrom(DAY.minusDays(7));
        event.setShowUntil(DAY);
        return event;
    }

    private static Instant at(LocalDate date, String time) {
        return date.atTime(LocalTime.parse(time)).atZone(ZONE).toInstant();
    }

    @Test
    void shown_from_the_first_day_until_the_event_ends() {
        BusinessEvent event = event("21:00", "23:00");

        assertThat(event.showingAt(at(DAY.minusDays(8), "12:00"), ZONE)).isFalse();
        assertThat(event.showingAt(at(DAY.minusDays(7), "00:00"), ZONE)).isTrue();
        assertThat(event.showingAt(at(DAY, "22:00"), ZONE)).isTrue();
        assertThat(event.showingAt(at(DAY, "23:00"), ZONE)).isFalse();
    }

    @Test
    void an_end_before_the_start_is_after_midnight() {
        assertThat(event("22:00", "02:00").endsAt(ZONE)).isEqualTo(at(DAY.plusDays(1), "02:00"));
    }

    @Test
    void without_an_end_it_lasts_the_day() {
        BusinessEvent event = event("20:00", null);

        assertThat(event.endsAt(ZONE)).isEqualTo(at(DAY.plusDays(1), "00:00"));
        assertThat(event.startedAt(at(DAY, "19:59"), ZONE)).isFalse();
        assertThat(event.startedAt(at(DAY, "20:00"), ZONE)).isTrue();
    }
}
