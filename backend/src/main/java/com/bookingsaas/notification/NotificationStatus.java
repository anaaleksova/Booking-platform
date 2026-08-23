package com.bookingsaas.notification;

public enum NotificationStatus {

    /** Waiting in the outbox for the dispatcher. */
    PENDING,
    SENT,
    /** Retries exhausted; kept for support, never retried automatically again. */
    FAILED,
    /** Not attempted — the tenant has this kind of notification switched off. */
    SKIPPED
}
