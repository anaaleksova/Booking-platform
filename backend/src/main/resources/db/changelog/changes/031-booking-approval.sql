--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 031: services whose online bookings the owner approves first. Such a
-- booking is made PENDING: it holds its slot, and the customer is told it is
-- a request until the owner confirms or declines it.
-- ---------------------------------------------------------------------------

--changeset booking-platform:031-service-approval
ALTER TABLE service ADD COLUMN approval_required BOOLEAN NOT NULL DEFAULT FALSE;
-- With approval on: only groups of at least this size need it. NULL = every booking.
ALTER TABLE service ADD COLUMN approval_from_participants INTEGER;
ALTER TABLE service ADD CONSTRAINT ck_service_approval_from
    CHECK (approval_from_participants IS NULL OR approval_from_participants >= 1);

--changeset booking-platform:031-request-notifications
ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN (
    'RESERVATION_CONFIRMED_CUSTOMER',
    'RESERVATION_CONFIRMED_BUSINESS',
    'RESERVATION_CANCELLED_CUSTOMER',
    'RESERVATION_CANCELLED_BUSINESS',
    'RESERVATION_RESCHEDULED_CUSTOMER',
    'RESERVATION_RESCHEDULED_BUSINESS',
    'RESERVATION_REMINDER_CUSTOMER',
    'RESERVATION_CONFIRMED_STAFF',
    'RESERVATION_RESCHEDULED_STAFF',
    'RESERVATION_CANCELLED_STAFF',
    'RESERVATION_UNASSIGNED_STAFF',
    'RESERVATION_REQUESTED_CUSTOMER',
    'RESERVATION_REQUESTED_BUSINESS',
    'RESERVATION_DECLINED_CUSTOMER'
));
