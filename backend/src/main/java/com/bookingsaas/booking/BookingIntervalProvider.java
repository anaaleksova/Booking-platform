package com.bookingsaas.booking;

import com.bookingsaas.availability.spi.BookedIntervalProvider;
import com.bookingsaas.availability.spi.OccupiedInterval;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The booking module's implementation of what availability needs to know.
 *
 * <p>The dependency points this way — booking knows about availability, never
 * the reverse — so the engine can be reasoned about (and tested) without any
 * booking persistence at all.
 */
@Component
public class BookingIntervalProvider implements BookedIntervalProvider {

    /** No booking id is ever 0, so this excludes nothing. */
    private static final long EXCLUDE_NOTHING = 0L;

    private final BookingRepository repository;

    public BookingIntervalProvider(BookingRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccupiedInterval> occupiedIntervals(Long businessId, Instant from, Instant to,
                                                    Long excludeBookingId) {
        return repository.findOccupying(businessId, from, to, BookingStatus.OCCUPYING,
                        excludeBookingId == null ? EXCLUDE_NOTHING : excludeBookingId).stream()
                .map(booking -> new OccupiedInterval(booking.getId(), booking.getStaffId(),
                        booking.getResourceId(), booking.blockRange()))
                .toList();
    }
}
