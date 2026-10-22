--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 023: employees who can log in hear about their own appointments: new,
-- moved, cancelled, or given to someone else.
-- ---------------------------------------------------------------------------

--changeset booking-platform:023-staff-notifications
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
    'RESERVATION_UNASSIGNED_STAFF'
));
--rollback ALTER TABLE notification DROP CONSTRAINT ck_notification_type;
--rollback ALTER TABLE notification ADD CONSTRAINT ck_notification_type CHECK (type IN ('RESERVATION_CONFIRMED_CUSTOMER', 'RESERVATION_CONFIRMED_BUSINESS', 'RESERVATION_CANCELLED_CUSTOMER', 'RESERVATION_CANCELLED_BUSINESS', 'RESERVATION_RESCHEDULED_CUSTOMER', 'RESERVATION_RESCHEDULED_BUSINESS', 'RESERVATION_REMINDER_CUSTOMER'));
