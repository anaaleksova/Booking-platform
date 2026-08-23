-- ---------------------------------------------------------------------------
-- V5: notification outbox.
--
-- Booking logic writes rows here and never talks to SMTP directly. A worker
-- drains the table, so a mail outage cannot fail a booking, and adding
-- SMS/WhatsApp/Viber later means adding a channel value plus a sender bean.
-- ---------------------------------------------------------------------------

CREATE TABLE notifications (
    id            BIGSERIAL PRIMARY KEY,
    business_id   BIGINT       NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    booking_id    BIGINT       REFERENCES bookings (id) ON DELETE CASCADE,
    channel       VARCHAR(16)  NOT NULL,
    type          VARCHAR(40)  NOT NULL,
    recipient     VARCHAR(320) NOT NULL,
    subject       VARCHAR(255),
    body          TEXT         NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts      INTEGER      NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000),
    scheduled_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('EMAIL', 'SMS', 'WHATSAPP', 'VIBER')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'BOOKING_CONFIRMED_CUSTOMER',
        'BOOKING_CONFIRMED_BUSINESS',
        'BOOKING_CANCELLED_CUSTOMER',
        'BOOKING_CANCELLED_BUSINESS',
        'BOOKING_RESCHEDULED_CUSTOMER',
        'BOOKING_REMINDER_CUSTOMER'
    ))
);

CREATE INDEX idx_notifications_dispatch ON notifications (status, scheduled_at) WHERE status = 'PENDING';
CREATE INDEX idx_notifications_business ON notifications (business_id, created_at DESC);
CREATE INDEX idx_notifications_booking ON notifications (booking_id) WHERE booking_id IS NOT NULL;

-- One reminder per booking, guarded by the database rather than by a
-- "did we already send this?" query that races with itself.
CREATE UNIQUE INDEX uq_notifications_booking_reminder
    ON notifications (booking_id, type)
    WHERE booking_id IS NOT NULL AND type = 'BOOKING_REMINDER_CUSTOMER';
