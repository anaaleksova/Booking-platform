--liquibase formatted sql

-- ---------------------------------------------------------------------------
-- 018: where the business itself hears about new and cancelled reservations.
-- The business email is its public contact address (customers reply to it);
-- the owner may want these alerts somewhere else. Empty means: the owner's
-- login email.
-- ---------------------------------------------------------------------------

--changeset booking-platform:018-notification-email
ALTER TABLE business_settings ADD COLUMN notification_email VARCHAR(320);
--rollback ALTER TABLE business_settings DROP COLUMN notification_email;
