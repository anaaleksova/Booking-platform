--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 004: notification outbox.
--
-- Reservation logic writes rows here and never talks to SMTP directly. A
-- worker drains the table, so a mail outage cannot fail a reservation.
-- ---------------------------------------------------------------------------

--changeset booking-platform:004-notification
CREATE TABLE notification (
    id             BIGSERIAL PRIMARY KEY,
    business_id    BIGINT       NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    reservation_id BIGINT       REFERENCES reservation (id) ON DELETE CASCADE,
    channel        VARCHAR(16)  NOT NULL,
    type           VARCHAR(40)  NOT NULL,
    recipient      VARCHAR(320) NOT NULL,
    subject        VARCHAR(255),
    body           TEXT         NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000),
    scheduled_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL', 'SMS', 'WHATSAPP', 'VIBER')),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_notification_type CHECK (type IN (
        'RESERVATION_CONFIRMED_CUSTOMER',
        'RESERVATION_CONFIRMED_BUSINESS',
        'RESERVATION_CANCELLED_CUSTOMER',
        'RESERVATION_CANCELLED_BUSINESS',
        'RESERVATION_RESCHEDULED_CUSTOMER',
        'RESERVATION_REMINDER_CUSTOMER'
    ))
);

CREATE INDEX idx_notification_dispatch ON notification (status, scheduled_at) WHERE status = 'PENDING';
CREATE INDEX idx_notification_business ON notification (business_id, created_at DESC);
CREATE INDEX idx_notification_reservation ON notification (reservation_id) WHERE reservation_id IS NOT NULL;

-- One reminder per reservation, guarded by the database.
CREATE UNIQUE INDEX uq_notification_reservation_reminder
    ON notification (reservation_id, type)
    WHERE reservation_id IS NOT NULL AND type = 'RESERVATION_REMINDER_CUSTOMER';
