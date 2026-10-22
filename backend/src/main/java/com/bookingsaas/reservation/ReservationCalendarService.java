package com.bookingsaas.reservation;

import com.bookingsaas.common.calendar.CustomerEvent;
import com.bookingsaas.common.calendar.Ics;
import com.bookingsaas.common.config.AppProperties;
import com.bookingsaas.reservation.dto.PublicReservationResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

/** A customer's reservation as a calendar file they can add to their own calendar. */
@Service
public class ReservationCalendarService {

    private final AppProperties properties;
    private final Clock clock;

    public ReservationCalendarService(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String customerEvent(PublicReservationResponse reservation) {
        String with = reservation.resources().stream().map(PublicReservationResponse.Resource::name)
                .collect(Collectors.joining(", "));
        return Ics.calendar(List.of(CustomerEvent.of(reservation.reference().toString(),
                reservation.businessName(), reservation.businessAddress(), reservation.businessPhone(),
                reservation.serviceName(), with, reservation.startTime(), reservation.endTime(),
                "CANCELLED".equals(reservation.status()), properties.frontendUrl())), clock.instant());
    }
}
