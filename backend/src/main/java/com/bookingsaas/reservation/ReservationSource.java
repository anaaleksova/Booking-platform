package com.bookingsaas.reservation;

/**
 * The channel a reservation came through: the customer website, the business
 * itself, a social channel via the automation layer, or another API client.
 */
public enum ReservationSource {
    WEB,
    ADMIN,
    INSTAGRAM,
    FACEBOOK,
    API
}
