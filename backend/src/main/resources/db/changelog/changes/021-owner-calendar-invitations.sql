--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 021: the owner's calendar follows reservations through calendar invitations
-- in the emails they already get, instead of a subscription link (020). A
-- moved reservation now also tells the business, so its event moves.
-- ---------------------------------------------------------------------------

--changeset booking-platform:021-owner-calendar-invitations
DROP INDEX IF EXISTS uq_business_settings_calendar_feed_token;
ALTER TABLE business_settings DROP COLUMN IF EXISTS calendar_feed_token;
ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN (
    'RESERVATION_CONFIRMED_CUSTOMER',
    'RESERVATION_CONFIRMED_BUSINESS',
    'RESERVATION_CANCELLED_CUSTOMER',
    'RESERVATION_CANCELLED_BUSINESS',
    'RESERVATION_RESCHEDULED_CUSTOMER',
    'RESERVATION_RESCHEDULED_BUSINESS',
    'RESERVATION_REMINDER_CUSTOMER'
));
--rollback ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
--rollback ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN ('RESERVATION_CONFIRMED_CUSTOMER', 'RESERVATION_CONFIRMED_BUSINESS', 'RESERVATION_CANCELLED_CUSTOMER', 'RESERVATION_CANCELLED_BUSINESS', 'RESERVATION_RESCHEDULED_CUSTOMER', 'RESERVATION_REMINDER_CUSTOMER'));
--rollback ALTER TABLE business_settings ADD COLUMN calendar_feed_token VARCHAR(64);
