package com.bookingsaas.integration;

import com.bookingsaas.reservation.ReservationSource;

/**
 * A messaging channel a customer can reach the business through. {@code TEST}
 * is the n8n test webhook, used to try a workflow before a real channel is
 * connected.
 */
public enum ChannelProvider {
    INSTAGRAM,
    FACEBOOK,
    TEST;

    /** What a reservation made through this channel is attributed to in analytics. */
    public ReservationSource reservationSource() {
        return switch (this) {
            case INSTAGRAM -> ReservationSource.INSTAGRAM;
            case FACEBOOK -> ReservationSource.FACEBOOK;
            case TEST -> ReservationSource.API;
        };
    }
}
